"""
config_resolver.py — Platform & LLM Configuration Resolver
============================================================
Implements the Subscription-First principle:
  - Autodetects the active IDE/platform by scanning known root directories.
  - Parses TOML-like sections from .ai/agents-config.md (and local overrides).
  - Defaults to provider = "native" — no external paid API keys required.
  - Local overrides (.ai/agent-config.local.md) are always applied last.

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import re
import dataclasses
from pathlib import Path
from typing import Optional


# ---------------------------------------------------------------------------
# Data Models
# ---------------------------------------------------------------------------

@dataclasses.dataclass
class ProfileConfig:
    """Resolved LLM profile configuration for a single model tier."""
    profile_name: str            # "REASONING_HEAVY" | "STRICT_CODEGEN" | "FAST_EXECUTION"
    provider: str                # "native" | "ollama" | "openai" | "anthropic" | …
    preferred_model: str         # model identifier or "native-environment-model"
    fallback_model: str          # fallback if preferred is unavailable
    temperature: float           # sampling temperature
    max_output_tokens: int       # max generation tokens
    base_url: Optional[str]      # override endpoint (e.g. http://localhost:11434/v1)


@dataclasses.dataclass
class OrchestratorConfig:
    """Full resolved configuration for the orchestrator session."""
    platform: str                          # detected platform name
    platform_adapter_dir: Optional[Path]   # path to the platform adapter dir
    profiles: dict[str, ProfileConfig]     # keyed by profile_name

    def get_profile(self, profile_name: str) -> ProfileConfig:
        if profile_name not in self.profiles:
            raise ValueError(
                f"Unknown profile '{profile_name}'. "
                f"Available: {list(self.profiles.keys())}"
            )
        return self.profiles[profile_name]


# ---------------------------------------------------------------------------
# Known platforms (order matters: first match wins)
# ---------------------------------------------------------------------------

_PLATFORM_DIRS: list[tuple[str, str]] = [
    (".antigravity", "antigravity"),
    (".cursor",      "cursor"),
    (".claude",      "claude-code"),
    (".windsurf",    "windsurf"),
]

# Default profile values when a key is absent from the config
_DEFAULTS: dict[str, object] = {
    "provider":           "native",
    "preferred_model":    "native-environment-model",
    "fallback_model":     "native-environment-model",
    "temperature":        "0.2",
    "max_output_tokens":  "8192",
    "base_url":           "",
}

# TOML-like section header, e.g.  [REASONING_HEAVY]
_SECTION_RE = re.compile(r"^\[([A-Z_]+)\]\s*$")
# Key = value line
_KV_RE = re.compile(r"^(\w+)\s*=\s*\"?([^\"#\n]*)\"?\s*(?:#.*)?$")


# ---------------------------------------------------------------------------
# Internal parsers
# ---------------------------------------------------------------------------

def _parse_toml_sections(text: str) -> dict[str, dict[str, str]]:
    """
    Extract TOML-like sections embedded inside a Markdown fenced code block
    or bare in the file.  Returns {section_name: {key: value}}.
    """
    # Strip fenced code blocks markers if present, keeping contents
    text = re.sub(r"```[a-z]*", "", text)

    sections: dict[str, dict[str, str]] = {}
    current: Optional[str] = None

    for line in text.splitlines():
        line = line.rstrip()
        m_sec = _SECTION_RE.match(line)
        if m_sec:
            current = m_sec.group(1)
            sections.setdefault(current, {})
            continue
        if current is None:
            continue
        m_kv = _KV_RE.match(line)
        if m_kv:
            sections[current][m_kv.group(1).strip()] = m_kv.group(2).strip()

    return sections


def _build_profile(name: str, raw: dict[str, str]) -> ProfileConfig:
    merged = {**_DEFAULTS, **raw}   # raw overrides defaults
    base_url = merged["base_url"] or None
    return ProfileConfig(
        profile_name=name,
        provider=str(merged["provider"]),
        preferred_model=str(merged["preferred_model"]),
        fallback_model=str(merged["fallback_model"]),
        temperature=float(merged["temperature"]),
        max_output_tokens=int(str(merged["max_output_tokens"]).strip()),
        base_url=base_url if base_url else None,
    )


def _detect_platform(repo_root: Path) -> tuple[str, Optional[Path]]:
    """Return (platform_name, adapter_path) for the first detected platform."""
    for dir_name, platform_name in _PLATFORM_DIRS:
        candidate = repo_root / dir_name
        if candidate.is_dir():
            return platform_name, candidate
    return "generic", None


def _load_text_safe(path: Path) -> str:
    """Read a file or return empty string if it doesn't exist."""
    if path.exists():
        return path.read_text(encoding="utf-8")
    return ""


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

_EXPECTED_PROFILES = {"REASONING_HEAVY", "STRICT_CODEGEN", "FAST_EXECUTION"}


def load_config(repo_root: str | Path) -> OrchestratorConfig:
    """
    Resolve the full orchestrator configuration from the repository root.

    Priority (highest last = wins):
      1. Built-in defaults (provider = "native").
      2. .ai/agents-config.md   — shared team manifest.
      3. .ai/agent-config.local.md — personal/machine overrides (gitignored).

    Args:
        repo_root: Absolute path to the repository root.

    Returns:
        OrchestratorConfig with all three profiles resolved.

    Raises:
        FileNotFoundError: if the primary manifest .ai/agents-config.md is missing.
    """
    repo_root = Path(repo_root)
    platform, adapter_dir = _detect_platform(repo_root)

    primary_path = repo_root / ".ai" / "agents-config.md"
    local_path   = repo_root / ".ai" / "agent-config.local.md"

    if not primary_path.exists():
        raise FileNotFoundError(
            f"Primary LLM manifest not found: {primary_path}. "
            "Ensure .ai/agents-config.md exists in the repository root."
        )

    # Parse primary then overlay local
    base_sections  = _parse_toml_sections(_load_text_safe(primary_path))
    local_sections = _parse_toml_sections(_load_text_safe(local_path))

    # Deep-merge: local values override base values per-key inside each section
    merged: dict[str, dict[str, str]] = {}
    all_sections = set(base_sections) | set(local_sections)
    for sec in all_sections:
        merged[sec] = {**base_sections.get(sec, {}), **local_sections.get(sec, {})}

    # Build a ProfileConfig for every known profile; missing ones get defaults
    profiles: dict[str, ProfileConfig] = {}
    for prof_name in _EXPECTED_PROFILES:
        raw = merged.get(prof_name, {})
        profiles[prof_name] = _build_profile(prof_name, raw)

    return OrchestratorConfig(
        platform=platform,
        platform_adapter_dir=adapter_dir,
        profiles=profiles,
    )


def print_config_summary(config: OrchestratorConfig) -> None:
    """Human-readable config dump for orchestrator startup banner."""
    print(f"  Platform detected : {config.platform}")
    if config.platform_adapter_dir:
        print(f"  Adapter directory : {config.platform_adapter_dir}")
    for name, prof in config.profiles.items():
        print(f"  [{name}]")
        print(f"    provider      = {prof.provider}")
        print(f"    model         = {prof.preferred_model}")
        if prof.base_url:
            print(f"    base_url      = {prof.base_url}")
        print(f"    temperature   = {prof.temperature}")
        print(f"    max_tokens    = {prof.max_output_tokens}")


# ---------------------------------------------------------------------------
# CLI helper
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import sys

    root = sys.argv[1] if len(sys.argv) > 1 else "."
    cfg = load_config(root)
    print_config_summary(cfg)
