"""
git_engine.py — Git Checkpoints, Branch Protection & Rollback Engine
====================================================================
Provides deterministic, safe Git operations for the orchestrator:

  - Branch Protection: blocks execution on protected branches unless
    --git-auto creates an agent-feature-{feature} branch automatically.
  - Checkpoints: captures the current HEAD SHA before each step.
  - Rollback: resets the working tree to the checkpoint SHA on failure.
  - Commits: creates an atomic commit after each successful step.

Zero external dependencies — stdlib only (subprocess + pathlib).
"""

from __future__ import annotations

import re
import subprocess
import dataclasses
from pathlib import Path
from typing import Optional


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

_PROTECTED_BRANCH_PATTERNS: list[re.Pattern] = [
    re.compile(r"^main$"),
    re.compile(r"^master$"),
    re.compile(r"^develop$"),
    re.compile(r"^stage.*"),
    re.compile(r"^release.*"),
    re.compile(r"^production$"),
]

_AGENT_BRANCH_TEMPLATE = "agent-feature-{feature}"


# ---------------------------------------------------------------------------
# Data Models
# ---------------------------------------------------------------------------

@dataclasses.dataclass
class GitCheckpoint:
    """A snapshot of the repository state before a pipeline step."""
    step_id: str
    sha: str          # full 40-char commit SHA
    branch: str       # branch name at snapshot time


@dataclasses.dataclass
class GitResult:
    """Result of a git operation."""
    ok: bool
    output: str
    error: Optional[str]


# ---------------------------------------------------------------------------
# Low-level git runner
# ---------------------------------------------------------------------------

def _git(args: list[str], cwd: Path) -> tuple[int, str, str]:
    """Run a git command; return (returncode, stdout, stderr)."""
    try:
        result = subprocess.run(
            ["git"] + args,
            cwd=str(cwd),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )
        stdout = result.stdout.decode("utf-8", errors="replace").strip()
        stderr = result.stderr.decode("utf-8", errors="replace").strip()
        return result.returncode, stdout, stderr
    except FileNotFoundError:
        return -1, "", "git executable not found on PATH"
    except subprocess.TimeoutExpired:
        return -1, "", "git command timed out"
    except Exception as exc:
        return -1, "", str(exc)


# ---------------------------------------------------------------------------
# Branch helpers
# ---------------------------------------------------------------------------

def current_branch(repo_root: Path) -> str:
    """Return the name of the currently checked-out branch."""
    code, stdout, stderr = _git(["rev-parse", "--abbrev-ref", "HEAD"], repo_root)
    if code != 0:
        raise RuntimeError(f"Cannot determine current branch: {stderr}")
    return stdout.strip()


def is_protected(branch: str) -> bool:
    """Return True if the branch matches any protected pattern."""
    for pattern in _PROTECTED_BRANCH_PATTERNS:
        if pattern.match(branch):
            return True
    return False


def agent_branch_name(feature: str) -> str:
    return _AGENT_BRANCH_TEMPLATE.format(feature=feature)


def branch_exists(repo_root: Path, branch: str) -> bool:
    code, stdout, _ = _git(["branch", "--list", branch], repo_root)
    return code == 0 and branch in stdout


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def ensure_agent_branch(
    repo_root: Path,
    feature: str,
    git_auto: bool,
) -> GitResult:
    branch = current_branch(repo_root)

    if not is_protected(branch):
        return GitResult(ok=True, output=f"Active branch: {branch}", error=None)

    target = agent_branch_name(feature)

    if not git_auto:
        cmd_hint = f"git checkout -b {target}"
        return GitResult(
            ok=False,
            output="",
            error=(
                f"Branch protection triggered: '{branch}' is a protected branch. "
                f"The orchestrator is not allowed to write directly to it.\n\n"
                f"  To proceed, switch to a dedicated agent branch first:\n"
                f"    {cmd_hint}\n\n"
                f"  Or re-run with --git-auto to let the orchestrator create it automatically."
            ),
        )

    if branch_exists(repo_root, target):
        code, out, err = _git(["checkout", target], repo_root)
    else:
        code, out, err = _git(["checkout", "-b", target], repo_root)

    if code != 0:
        return GitResult(ok=False, output=out, error=f"Failed to switch branch: {err}")

    return GitResult(
        ok=True,
        output=f"Switched to agent branch: {target}",
        error=None,
    )


def create_checkpoint(repo_root: Path, step_id: str) -> GitCheckpoint:
    code, sha, stderr = _git(["rev-parse", "HEAD"], repo_root)
    if code != 0:
        raise RuntimeError(
            f"Cannot create checkpoint for step {step_id}: {stderr}. "
            "The repository must have at least one commit."
        )
    branch = current_branch(repo_root)
    return GitCheckpoint(step_id=step_id, sha=sha.strip(), branch=branch)


def rollback(repo_root: Path, checkpoint: GitCheckpoint) -> GitResult:
    _git(["clean", "-fd"], repo_root)
    code, out, err = _git(["reset", "--hard", checkpoint.sha], repo_root)
    if code != 0:
        return GitResult(
            ok=False, output=out,
            error=f"Rollback to {checkpoint.sha[:8]} failed: {err}"
        )
    return GitResult(
        ok=True,
        output=f"Rolled back to checkpoint {checkpoint.sha[:8]} (step {checkpoint.step_id})",
        error=None,
    )


def commit_step(
    repo_root: Path,
    step_id: str,
    agent_name: str,
    summary: str,
) -> GitResult:
    code, status_out, _ = _git(["status", "--porcelain"], repo_root)
    if code == 0 and not status_out.strip():
        return GitResult(
            ok=True,
            output=f"Step {step_id}: no file changes to commit.",
            error=None,
        )

    code, out, err = _git(["add", "-A"], repo_root)
    if code != 0:
        return GitResult(ok=False, output=out, error=f"git add failed: {err}")

    short_name = agent_name.replace("-", " ").title()
    message = (
        f"feat(step-{step_id.zfill(2)}): {short_name} output finalized\n\n"
        f"{summary}"
    )

    code, out, err = _git(["commit", "-m", message], repo_root)
    if code != 0:
        return GitResult(ok=False, output=out, error=f"git commit failed: {err}")

    _, new_sha, _ = _git(["rev-parse", "--short", "HEAD"], repo_root)
    return GitResult(
        ok=True,
        output=f"Committed step {step_id} as {new_sha}: {short_name}",
        error=None,
    )


def has_uncommitted_changes(repo_root: Path) -> bool:
    code, out, _ = _git(["status", "--porcelain"], repo_root)
    return code == 0 and bool(out.strip())


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 3:
        print("Usage: python git_engine.py <repo_root> <feature-name> [--git-auto]")
        sys.exit(1)

    root = Path(sys.argv[1])
    feat = sys.argv[2]
    auto = "--git-auto" in sys.argv

    result = ensure_agent_branch(root, feat, git_auto=auto)
    print(result.output or result.error)
    sys.exit(0 if result.ok else 1)
