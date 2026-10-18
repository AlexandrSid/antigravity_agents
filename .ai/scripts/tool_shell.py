"""
tool_shell.py — Bounded Local Tool Shell & Write Scope Validator
================================================================
Provides the deterministic, safe execution environment for a single pipeline
step. The shell:

  1. Parses the Write Scope and Forbidden Scope declared in the agent's role
     file (.ai/rules/{agent}.md).
  2. Exposes three tools to the agent: read_artifact, apply_patch, complete_step.
  3. Enforces Write Scope on every apply_patch call; returns a structured
     permission-denied error instead of writing to disk.
  4. Limits the total number of tool calls per step to MAX_ITERATIONS (5).

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import re
import dataclasses
from pathlib import Path
from typing import List, Optional


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

MAX_ITERATIONS: int = 5


# ---------------------------------------------------------------------------
# Data Models
# ---------------------------------------------------------------------------

@dataclasses.dataclass
class ToolResult:
    """Unified return value from every tool call."""
    ok: bool
    content: str          # file content on read / confirmation on write
    error: Optional[str]  # human-readable error message if ok == False


@dataclasses.dataclass
class StepSession:
    """Tracks the state of a single agent step execution."""
    agent_role: str
    rule_file: Path
    repo_root: Path
    allowed_write_globs: List[str]   # patterns from Allowed Scope
    forbidden_globs: List[str]       # patterns from Forbidden Scope
    iteration: int = 0
    completed: bool = False
    completion_summary: str = ""


# ---------------------------------------------------------------------------
# Write-Scope parser
# ---------------------------------------------------------------------------

# Matches backtick-quoted paths like `src/main/**`
_PATH_PATTERN_RE = re.compile(r"`([^`]+)`")

# Section heading matchers
_ALLOWED_SECTION_RE  = re.compile(
    r"###\s*Allowed Scope.*?(?=###|\Z)", re.DOTALL | re.IGNORECASE
)
_FORBIDDEN_SECTION_RE = re.compile(
    r"###\s*Forbidden Scope.*?(?=###|\Z)", re.DOTALL | re.IGNORECASE
)


def _extract_paths_from_section(section_text: str) -> List[str]:
    return _PATH_PATTERN_RE.findall(section_text)


def parse_write_scope(rule_file: Path) -> tuple[List[str], List[str]]:
    """
    Parse Allowed Scope and Forbidden Scope from an agent rule Markdown file.

    Returns:
        (allowed_globs, forbidden_globs) — lists of glob patterns or path prefixes.

    Raises:
        FileNotFoundError: if rule_file does not exist.
        ValueError: if neither an Allowed nor Forbidden Scope section is found.
    """
    if not rule_file.exists():
        raise FileNotFoundError(f"Agent rule file not found: {rule_file}")

    text = rule_file.read_text(encoding="utf-8")

    allowed_match  = _ALLOWED_SECTION_RE.search(text)
    forbidden_match = _FORBIDDEN_SECTION_RE.search(text)

    if not allowed_match and not forbidden_match:
        raise ValueError(
            f"No 'Allowed Scope' or 'Forbidden Scope' sections found in {rule_file}. "
            "The rule file must declare access zones."
        )

    allowed   = _extract_paths_from_section(allowed_match.group(0))  if allowed_match  else []
    forbidden = _extract_paths_from_section(forbidden_match.group(0)) if forbidden_match else []

    return allowed, forbidden


# ---------------------------------------------------------------------------
# Glob-like path matching (no fnmatch — stdlib pathlib.match is used)
# ---------------------------------------------------------------------------

def _path_matches_any(path_str: str, patterns: List[str]) -> bool:
    """
    Return True if *path_str* matches any pattern in *patterns*.
    Supports:
      - Exact matches        ("docs/features/x/SPEC.md")
      - Prefix matches       ("src/main/") — path starts with the prefix
      - Glob /**             ("src/main/**") — path starts with the prefix before /**
      - Single *             ("docs/features/*/SPEC.md") via pathlib.PurePosixPath.match
    """
    from pathlib import PurePosixPath
    p = PurePosixPath(path_str)
    for pattern in patterns:
        # Normalize Windows separators
        pattern = pattern.replace("\\", "/").strip()
        if not pattern:
            continue
        # Prefix ending with /
        if pattern.endswith("/") and path_str.startswith(pattern):
            return True
        # Glob suffix /**
        if pattern.endswith("/**"):
            prefix = pattern[:-3]  # strip /**
            if path_str.startswith(prefix):
                return True
        # Exact or glob via pathlib
        try:
            if p.match(pattern):
                return True
        except Exception:
            pass
        # Fallback: startswith for bare directory names
        if path_str.startswith(pattern):
            return True
    return False


# ---------------------------------------------------------------------------
# Tool implementations
# ---------------------------------------------------------------------------

def tool_read_artifact(session: StepSession, path: str) -> ToolResult:
    """
    Read the content of a file relative to repo_root.
    Any path within the repo is readable — the tool shell does not restrict reads.
    """
    session.iteration += 1
    if session.iteration > MAX_ITERATIONS:
        return ToolResult(
            ok=False, content="",
            error=f"Iteration limit exceeded ({MAX_ITERATIONS}). Step must call complete_step()."
        )

    full_path = session.repo_root / path
    if not full_path.exists():
        return ToolResult(ok=False, content="", error=f"File not found: {path}")
    if full_path.is_dir():
        return ToolResult(ok=False, content="", error=f"Path is a directory: {path}")

    try:
        content = full_path.read_text(encoding="utf-8")
        return ToolResult(ok=True, content=content, error=None)
    except Exception as exc:
        return ToolResult(ok=False, content="", error=str(exc))


def tool_apply_patch(
    session: StepSession,
    path: str,
    new_content: str,
) -> ToolResult:
    """
    Write *new_content* to *path* (relative to repo_root) after validating
    the path against the agent's Write Scope.

    Write Scope enforcement logic:
      1. Path must match at least one allowed_write_glob.
      2. Path must NOT match any forbidden_glob.
    Both checks are required — forbidden always wins over allowed.
    """
    session.iteration += 1
    if session.iteration > MAX_ITERATIONS:
        return ToolResult(
            ok=False, content="",
            error=f"Iteration limit exceeded ({MAX_ITERATIONS}). Step must call complete_step()."
        )

    norm_path = path.replace("\\", "/")

    # Forbidden scope check (takes priority)
    if session.forbidden_globs and _path_matches_any(norm_path, session.forbidden_globs):
        return ToolResult(
            ok=False, content="",
            error=(
                f"Permission denied: '{path}' is in the Forbidden Scope "
                f"for role '{session.agent_role}'. "
                "Modify only files within your Allowed Scope."
            ),
        )

    # Allowed scope check
    if session.allowed_write_globs and not _path_matches_any(norm_path, session.allowed_write_globs):
        return ToolResult(
            ok=False, content="",
            error=(
                f"Permission denied: '{path}' is outside the Write Scope "
                f"for role '{session.agent_role}'. "
                f"Allowed patterns: {session.allowed_write_globs}"
            ),
        )

    # Perform the write
    full_path = session.repo_root / path
    try:
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(new_content, encoding="utf-8")
        return ToolResult(
            ok=True,
            content=f"Written {len(new_content)} chars to {path}",
            error=None,
        )
    except Exception as exc:
        return ToolResult(ok=False, content="", error=str(exc))


def tool_complete_step(session: StepSession, summary: str) -> ToolResult:
    """
    Signal that the agent has finished its work for this step.
    Must be called exactly once; subsequent calls are no-ops.
    """
    session.iteration += 1
    if session.completed:
        return ToolResult(ok=True, content="Step already marked complete.", error=None)
    session.completed = True
    session.completion_summary = summary.strip()
    return ToolResult(ok=True, content=f"Step complete: {session.completion_summary}", error=None)


# ---------------------------------------------------------------------------
# Session factory
# ---------------------------------------------------------------------------

def create_session(
    agent_role: str,
    rule_file: str | Path,
    repo_root: str | Path,
) -> StepSession:
    """
    Create a fresh StepSession by parsing the Write Scope from the rule file.

    Args:
        agent_role: Human-readable role name (e.g. "code-writer").
        rule_file:  Path to the agent's rule Markdown file.
        repo_root:  Repository root for resolving relative paths.

    Returns:
        Initialized StepSession ready for tool calls.
    """
    rule_file = Path(rule_file)
    repo_root = Path(repo_root)

    allowed, forbidden = parse_write_scope(rule_file)

    return StepSession(
        agent_role=agent_role,
        rule_file=rule_file,
        repo_root=repo_root,
        allowed_write_globs=allowed,
        forbidden_globs=forbidden,
    )


# ---------------------------------------------------------------------------
# Convenience dispatcher (for orchestrator integration)
# ---------------------------------------------------------------------------

def dispatch_tool(
    session: StepSession,
    tool_name: str,
    arguments: dict,
) -> ToolResult:
    """
    Route a tool call from the agent to the correct implementation.

    Expected tool_name values: "read_artifact", "apply_patch", "complete_step".
    Arguments are passed as a dict matching each tool's keyword parameters.
    """
    if tool_name == "read_artifact":
        return tool_read_artifact(session, arguments.get("path", ""))
    elif tool_name == "apply_patch":
        return tool_apply_patch(
            session,
            arguments.get("path", ""),
            arguments.get("new_content", ""),
        )
    elif tool_name == "complete_step":
        return tool_complete_step(session, arguments.get("summary", ""))
    else:
        return ToolResult(
            ok=False, content="",
            error=f"Unknown tool '{tool_name}'. Available: read_artifact, apply_patch, complete_step."
        )


# ---------------------------------------------------------------------------
# Tool schema (JSON-Schema format for LLM function calling)
# ---------------------------------------------------------------------------

TOOL_SCHEMAS: list[dict] = [
    {
        "name": "read_artifact",
        "description": (
            "Read the full content of a file in the repository. "
            "Use this to inspect input artifacts before making changes."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Relative path from the repository root (e.g. 'docs/features/user-service/SPEC.md')."
                }
            },
            "required": ["path"],
        },
    },
    {
        "name": "apply_patch",
        "description": (
            "Write or overwrite a file in the repository. "
            "Only files within your declared Write Scope are permitted. "
            "Provide the COMPLETE new file content — not a diff."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Relative path from the repository root."
                },
                "new_content": {
                    "type": "string",
                    "description": "Complete new content of the file."
                },
            },
            "required": ["path", "new_content"],
        },
    },
    {
        "name": "complete_step",
        "description": (
            "Signal that you have finished all work for this pipeline step. "
            "Call this exactly once when all your output artifacts are written."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "summary": {
                    "type": "string",
                    "description": "Brief human-readable summary of what was produced."
                }
            },
            "required": ["summary"],
        },
    },
]
