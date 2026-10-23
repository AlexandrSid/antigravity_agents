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


MAX_ITERATIONS: int = 5


@dataclasses.dataclass
class ToolResult:
    ok: bool
    content: str
    error: Optional[str]


@dataclasses.dataclass
class StepSession:
    agent_role: str
    rule_file: Path
    repo_root: Path
    allowed_write_globs: List[str]
    forbidden_globs: List[str]
    iteration: int = 0
    completed: bool = False
    completion_summary: str = ""


_PATH_PATTERN_RE = re.compile(r"`([^`]+)`")
_ALLOWED_SECTION_RE = re.compile(r"###\s*Allowed Scope.*?(?=###|\Z)", re.DOTALL | re.IGNORECASE)
_FORBIDDEN_SECTION_RE = re.compile(r"###\s*Forbidden Scope.*?(?=###|\Z)", re.DOTALL | re.IGNORECASE)


def _extract_paths_from_section(section_text: str) -> List[str]:
    return _PATH_PATTERN_RE.findall(section_text)


def parse_write_scope(rule_file: Path) -> tuple[List[str], List[str]]:
    if not rule_file.exists():
        raise FileNotFoundError(f"Agent rule file not found: {rule_file}")

    text = rule_file.read_text(encoding="utf-8")
    allowed_match = _ALLOWED_SECTION_RE.search(text)
    forbidden_match = _FORBIDDEN_SECTION_RE.search(text)

    if not allowed_match and not forbidden_match:
        raise ValueError(
            f"No 'Allowed Scope' or 'Forbidden Scope' sections found in {rule_file}."
        )

    allowed = _extract_paths_from_section(allowed_match.group(0)) if allowed_match else []
    forbidden = _extract_paths_from_section(forbidden_match.group(0)) if forbidden_match else []
    return allowed, forbidden


def _path_matches_any(path_str: str, patterns: List[str]) -> bool:
    from pathlib import PurePosixPath
    p = PurePosixPath(path_str)
    for pattern in patterns:
        pattern = pattern.replace("\\", "/").strip()
        if not pattern:
            continue
        if pattern.endswith("/") and path_str.startswith(pattern):
            return True
        if pattern.endswith("/**"):
            prefix = pattern[:-3]
            if path_str.startswith(prefix):
                return True
        try:
            if p.match(pattern):
                return True
        except Exception:
            pass
        if path_str.startswith(pattern):
            return True
    return False


def tool_read_artifact(session: StepSession, path: str) -> ToolResult:
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
    session.iteration += 1
    if session.iteration > MAX_ITERATIONS:
        return ToolResult(
            ok=False, content="",
            error=f"Iteration limit exceeded ({MAX_ITERATIONS}). Step must call complete_step()."
        )

    norm_path = path.replace("\\", "/")

    if session.forbidden_globs and _path_matches_any(norm_path, session.forbidden_globs):
        return ToolResult(
            ok=False, content="",
            error=(
                f"Permission denied: '{path}' is in the Forbidden Scope "
                f"for role '{session.agent_role}'."
            ),
        )

    if session.allowed_write_globs and not _path_matches_any(norm_path, session.allowed_write_globs):
        return ToolResult(
            ok=False, content="",
            error=(
                f"Permission denied: '{path}' is outside the Write Scope "
                f"for role '{session.agent_role}'. "
                f"Allowed patterns: {session.allowed_write_globs}"
            ),
        )

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
    session.iteration += 1
    if session.completed:
        return ToolResult(ok=True, content="Step already marked complete.", error=None)
    session.completed = True
    session.completion_summary = summary.strip()
    return ToolResult(ok=True, content=f"Step complete: {session.completion_summary}", error=None)


def create_session(
    agent_role: str,
    rule_file: str | Path,
    repo_root: str | Path,
) -> StepSession:
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


def dispatch_tool(
    session: StepSession,
    tool_name: str,
    arguments: dict,
) -> ToolResult:
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


TOOL_SCHEMAS: list[dict] = [
    {
        "name": "read_artifact",
        "description": "Read the full content of a file in the repository.",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                    "type": "string",
                    "description": "Relative path from the repository root."
                }
            },
            "required": ["path"],
        },
    },
    {
        "name": "apply_patch",
        "description": "Write or overwrite a file in the repository within your Allowed Scope.",
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
        "description": "Signal completion of all work for this pipeline step.",
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
