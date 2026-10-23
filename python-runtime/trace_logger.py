"""
trace_logger.py — Execution Trace Logger & Cost Tracker
========================================================
Records a detailed, session-granular trace of every agent invocation into
docs/features/{feature}/EXECUTION_TRACE.json.

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import json
import time
import dataclasses
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional


# ---------------------------------------------------------------------------
# Price Registry  (USD per 1 000 000 tokens, as of mid-2026 public pricing)
# ---------------------------------------------------------------------------

_PRICE_REGISTRY: dict[tuple[str, str], tuple[float, float]] = {
    ("anthropic",   "claude-opus-4"):      (15.00, 75.00),
    ("anthropic",   "claude-sonnet-4"):    (3.00,  15.00),
    ("anthropic",   "claude-3-7-sonnet"):  (3.00,  15.00),
    ("anthropic",   "claude-3-5-sonnet"):  (3.00,  15.00),
    ("anthropic",   "claude-3-5-haiku"):   (0.80,   4.00),
    ("anthropic",   "claude-3-haiku"):     (0.25,   1.25),
    ("anthropic",   "claude-3-opus"):      (15.00,  75.00),

    ("openai",      "gpt-4o"):             (2.50,  10.00),
    ("openai",      "gpt-4o-mini"):        (0.15,   0.60),
    ("openai",      "gpt-4-turbo"):        (10.00,  30.00),
    ("openai",      "gpt-4"):              (30.00,  60.00),
    ("openai",      "gpt-3.5-turbo"):      (0.50,   1.50),
    ("openai",      "o1"):                 (15.00,  60.00),
    ("openai",      "o3"):                 (10.00,  40.00),

    ("google",      "gemini-2.5-pro"):     (1.25,  10.00),
    ("google",      "gemini-2.5-flash"):   (0.075,  0.30),
    ("google",      "gemini-2.0-pro"):     (1.25,  10.00),
    ("google",      "gemini-2.0-flash"):   (0.075,  0.30),
    ("google",      "gemini-3.8-flash"):   (0.075,  0.30),

    ("deepseek",    "deepseek-r1"):        (0.55,   2.19),
    ("deepseek",    "deepseek-v3"):        (0.27,   1.10),

    ("qwen",        "qwen-2.5-72b"):       (0.90,   0.90),
    ("qwen",        "qwen-max"):           (1.60,   6.40),
}


def _calculate_cost(provider: str, model: str, prompt_tokens: int, completion_tokens: int) -> float:
    if provider in ("native", "ollama", "lm_studio", "lm-studio", "local", ""):
        return 0.0

    provider_key = provider.lower().strip()
    model_key = model.lower().strip()

    best_match: Optional[tuple[float, float]] = None
    best_len = 0
    for (p, m_prefix), prices in _PRICE_REGISTRY.items():
        if p == provider_key and model_key.startswith(m_prefix):
            if len(m_prefix) > best_len:
                best_match = prices
                best_len = len(m_prefix)

    if best_match is None:
        return 0.0

    prompt_price, completion_price = best_match
    return (prompt_tokens / 1_000_000 * prompt_price) + (completion_tokens / 1_000_000 * completion_price)


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclasses.dataclass
class TraceEntry:
    step_id: str
    agent_role: str
    model_used: str
    provider: str
    timestamp_start: str      # ISO-8601 UTC
    timestamp_end: str        # ISO-8601 UTC
    duration_sec: float
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int
    cost_usd: float
    exit_status: str          # "SUCCESS" | "FAILED" | "RETRY_{n}"

    def to_dict(self) -> dict:
        return {
            "step_id":           self.step_id,
            "agent_role":        self.agent_role,
            "model_used":        self.model_used,
            "provider":          self.provider,
            "timestamp_start":   self.timestamp_start,
            "timestamp_end":     self.timestamp_end,
            "duration_sec":      round(self.duration_sec, 3),
            "tokens": {
                "prompt_tokens":     self.prompt_tokens,
                "completion_tokens": self.completion_tokens,
                "total_tokens":      self.total_tokens,
            },
            "cost_usd":   round(self.cost_usd, 6),
            "exit_status": self.exit_status,
        }


# ---------------------------------------------------------------------------
# Trace timer
# ---------------------------------------------------------------------------

class StepTimer:
    def __init__(self) -> None:
        self._start: float = 0.0
        self._end: float = 0.0
        self.start_iso: str = ""
        self.end_iso: str = ""

    def start(self) -> "StepTimer":
        self._start = time.monotonic()
        self.start_iso = datetime.now(timezone.utc).isoformat(timespec="seconds")
        return self

    def stop(self) -> float:
        self._end = time.monotonic()
        self.end_iso = datetime.now(timezone.utc).isoformat(timespec="seconds")
        return self.elapsed()

    def elapsed(self) -> float:
        return round(self._end - self._start, 3)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def record(
    trace_file: Path,
    step_id: str,
    agent_role: str,
    model_used: str,
    provider: str,
    timer: StepTimer,
    prompt_tokens: int,
    completion_tokens: int,
    exit_status: str,
) -> TraceEntry:
    cost = _calculate_cost(provider, model_used, prompt_tokens, completion_tokens)
    total = prompt_tokens + completion_tokens

    entry = TraceEntry(
        step_id=step_id,
        agent_role=agent_role,
        model_used=model_used,
        provider=provider,
        timestamp_start=timer.start_iso,
        timestamp_end=timer.end_iso,
        duration_sec=timer.elapsed(),
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        total_tokens=total,
        cost_usd=cost,
        exit_status=exit_status,
    )

    _append_to_file(trace_file, entry)
    return entry


def _append_to_file(trace_file: Path, entry: TraceEntry) -> None:
    trace_file.parent.mkdir(parents=True, exist_ok=True)

    existing: list[dict] = []
    if trace_file.exists():
        try:
            existing = json.loads(trace_file.read_text(encoding="utf-8"))
            if not isinstance(existing, list):
                existing = []
        except (json.JSONDecodeError, IOError):
            existing = []

    existing.append(entry.to_dict())

    tmp = trace_file.with_suffix(".tmp")
    tmp.write_text(json.dumps(existing, indent=2, ensure_ascii=False), encoding="utf-8")
    tmp.replace(trace_file)


def print_trace_summary(entry: TraceEntry) -> None:
    cost_str = f"${entry.cost_usd:.4f}" if entry.cost_usd > 0 else "$0.00 (native/local)"
    print(
        f"  ✔ Step {entry.step_id} [{entry.agent_role}] "
        f"{entry.duration_sec}s | "
        f"{entry.total_tokens} tokens | "
        f"{cost_str} | {entry.exit_status}"
    )


def session_totals(trace_file: Path) -> dict:
    if not trace_file.exists():
        return {"steps": 0, "total_tokens": 0, "total_cost_usd": 0.0, "total_duration_sec": 0.0}

    try:
        entries = json.loads(trace_file.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, IOError):
        entries = []

    return {
        "steps":              len(entries),
        "total_tokens":       sum(e.get("tokens", {}).get("total_tokens", 0) for e in entries),
        "total_cost_usd":     round(sum(e.get("cost_usd", 0.0) for e in entries), 6),
        "total_duration_sec": round(sum(e.get("duration_sec", 0.0) for e in entries), 3),
    }


def estimate_tokens(text: str) -> int:
    """Heuristic token estimator (~3.5 chars per token for code/markdown)."""
    if not text:
        return 0
    return max(1, int(len(text) / 3.5))


def estimate_files_tokens(file_paths: list[Path]) -> int:
    """Estimates total tokens across a list of existing files."""
    total_chars = 0
    for p in file_paths:
        if p.exists() and p.is_file():
            try:
                total_chars += len(p.read_text(encoding="utf-8", errors="replace"))
            except Exception:
                pass
    return estimate_tokens(" " * total_chars)

