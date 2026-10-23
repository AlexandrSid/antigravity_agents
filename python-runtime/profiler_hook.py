"""
profiler_hook.py — IDE Lifecycle Hook, Profiler & Command Auto-Approver
========================================================================
Invoked by Antigravity IDE lifecycle hooks defined in .agents/hooks.json.

Responsibilities:
  1. Captures telemetry (active step, agent role, model name, tool calls,
     timestamp, prompt context estimation) into docs/features/{feature}/PROFILING_LOG.jsonl.
  2. Updates a human-readable table in docs/features/{feature}/PROFILING_SUMMARY.md.
  3. PreToolUse Auto-Approver:
     Cross-references commands from `run_command` with the `Pre-Approved Command Allowlist`
     in docs/PROJECT_ENV.md. If matched, returns {"decision": "allow"} on stdout,
     eliminating repetitive manual confirmation prompts for standard project commands!

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import os
import sys
import json
import fnmatch
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional, List, Dict, Any

# Ensure stdout uses UTF-8 without crashing
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def _resolve_repo_root() -> Path:
    # Walk up from script location to find .git or AGENTS.md
    current = Path(__file__).resolve().parent
    while current.parent != current:
        if (current / "AGENTS.md").exists() or (current / ".git").exists():
            return current
        current = current.parent
    return Path.cwd()


REPO_ROOT = _resolve_repo_root()


def _find_active_feature_and_step(repo_root: Path) -> tuple[str, str, str]:
    """Scans docs/features/**/PIPELINE_STATE.md to find active feature and current step."""
    features_dir = repo_root / "docs" / "features"
    if not features_dir.exists():
        return "general", "00", "unknown"

    for state_file in features_dir.glob("*/PIPELINE_STATE.md"):
        try:
            text = state_file.read_text(encoding="utf-8")
            feature_name = state_file.parent.name
            current_step = "01"
            agent_role = "unknown"

            step_match = re.search(r">\s*\*\*Current Step\*\*:\s*`?(\d+)`?", text)
            if step_match:
                current_step = step_match.group(1).zfill(2)

            # Match role from the table for this step
            row_match = re.search(r"\|\s*\[(?: |>|x)\]\s*\*\*" + current_step + r"\*\*\s*\|\s*\*\*([^*]+)\*\*", text)
            if row_match:
                agent_role = row_match.group(1).strip()

            return feature_name, current_step, agent_role
        except Exception:
            continue

    return "general", "00", "unknown"


import re


def _load_allowlist(repo_root: Path) -> List[str]:
    """Extracts commands from ## Pre-Approved Command Allowlist in PROJECT_ENV.md."""
    env_file = repo_root / "docs" / "PROJECT_ENV.md"
    if not env_file.exists():
        env_file = repo_root / "PROJECT_ENV.md"
    if not env_file.exists():
        return []

    try:
        text = env_file.read_text(encoding="utf-8")
        in_allowlist = False
        allowlist = []
        for line in text.splitlines():
            stripped = line.strip()
            if "Pre-Approved Command Allowlist" in stripped:
                in_allowlist = True
                continue
            if in_allowlist and stripped.startswith("## "):
                break
            if in_allowlist and (stripped.startswith("-") or stripped.startswith("*")):
                cmd_match = re.search(r"`([^`]+)`", stripped)
                if cmd_match:
                    allowlist.append(cmd_match.group(1).strip())
        return allowlist
    except Exception:
        return []


def _is_command_allowed(command: str, allowlist: List[str]) -> bool:
    if not command:
        return False

    norm_cmd = command.strip().replace("/", "\\")

    for allowed in allowlist:
        norm_allowed = allowed.strip().replace("/", "\\")
        # Direct match or wildcard glob match
        if norm_cmd == norm_allowed or fnmatch.fnmatch(norm_cmd, norm_allowed):
            return True
        # If wildcard ends with *
        if norm_allowed.endswith("*") and norm_cmd.startswith(norm_allowed[:-1]):
            return True

    return False


def _append_log(log_file: Path, entry: dict):
    try:
        log_file.parent.mkdir(parents=True, exist_ok=True)
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except Exception:
        pass


def _update_summary_md(summary_file: Path, log_file: Path):
    if not log_file.exists():
        return

    try:
        events = []
        for line in log_file.read_text(encoding="utf-8").splitlines():
            if line.strip():
                try:
                    events.append(json.loads(line))
                except Exception:
                    pass

        # Aggregate by step
        steps: Dict[str, dict] = {}
        for ev in events:
            step = ev.get("step", "00")
            if step not in steps:
                steps[step] = {
                    "step": step,
                    "role": ev.get("role", "unknown"),
                    "model": ev.get("model", "auto"),
                    "tool_calls": 0,
                    "first_time": ev.get("timestamp", ""),
                    "last_time": ev.get("timestamp", ""),
                    "status": "IN_PROGRESS",
                }
            st = steps[step]
            st["last_time"] = ev.get("timestamp", "")
            if ev.get("event") == "PreToolUse":
                st["tool_calls"] += 1
            if ev.get("event") == "Stop":
                st["status"] = "COMPLETED"

        lines = [
            "# Framework Profiling Summary",
            "",
            "> **Generated by**: `python-runtime/profiler_hook.py`  ",
            f"> **Last Updated**: `{datetime.now(timezone.utc).isoformat(timespec='seconds')}`",
            "",
            "| Step | Agent Role | Model Used | Tool Calls | Status |",
            "|:---:|---|---|:---:|:---:|",
        ]

        for s_id in sorted(steps.keys()):
            s = steps[s_id]
            lines.append(f"| **{s['step']}** | `{s['role']}` | `{s['model']}` | {s['tool_calls']} | `{s['status']}` |")

        lines.append("")
        summary_file.write_text("\n".join(lines), encoding="utf-8")
    except Exception:
        pass


def main():
    event_type = "Generic"
    if "--event" in sys.argv:
        idx = sys.argv.index("--event")
        if idx + 1 < len(sys.argv):
            event_type = sys.argv[idx + 1]

    # Read stdin JSON payload from Antigravity IDE
    stdin_data = {}
    try:
        raw_input = sys.stdin.read()
        if raw_input.strip():
            stdin_data = json.loads(raw_input)
    except Exception:
        stdin_data = {}

    feature, step, role = _find_active_feature_and_step(REPO_ROOT)
    log_dir = REPO_ROOT / "docs" / "features" / feature
    log_file = log_dir / "PROFILING_LOG.jsonl"
    summary_file = log_dir / "PROFILING_SUMMARY.md"

    now_iso = datetime.now(timezone.utc).isoformat(timespec="seconds")
    model_name = stdin_data.get("modelName", "auto")

    # Handle PreToolUse
    if event_type == "PreToolUse":
        tool_call = stdin_data.get("toolCall", {})
        tool_name = tool_call.get("name", "")
        args = tool_call.get("args", {})
        cmd = args.get("CommandLine", "")

        entry = {
            "timestamp": now_iso,
            "event": "PreToolUse",
            "step": step,
            "role": role,
            "model": model_name,
            "tool": tool_name,
            "command": cmd if tool_name == "run_command" else "",
        }

        # Check Allowlist for run_command
        if tool_name == "run_command" and cmd:
            allowlist = _load_allowlist(REPO_ROOT)
            if _is_command_allowed(cmd, allowlist):
                entry["decision"] = "allow"
                entry["reason"] = "Pre-approved in PROJECT_ENV.md allowlist"
                _append_log(log_file, entry)
                _update_summary_md(summary_file, log_file)
                # Output to Antigravity
                print(json.dumps({
                    "decision": "allow",
                    "reason": "Pre-approved in PROJECT_ENV.md allowlist"
                }))
                sys.exit(0)

        # Default: record and ask
        entry["decision"] = "ask"
        _append_log(log_file, entry)
        _update_summary_md(summary_file, log_file)
        print(json.dumps({
            "decision": "ask",
            "reason": "Standard permission check"
        }))
        sys.exit(0)

    # Handle other events: PreInvocation, PostInvocation, PostToolUse, Stop
    entry = {
        "timestamp": now_iso,
        "event": event_type,
        "step": step,
        "role": role,
        "model": model_name,
    }
    _append_log(log_file, entry)
    _update_summary_md(summary_file, log_file)

    # Empty JSON response on stdout for default lifecycle events
    print(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
