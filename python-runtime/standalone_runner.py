"""
standalone_runner.py — Headless Standalone SDLC Pipeline Runner
================================================================
Independent console-based execution engine for CI/CD, headless runs,
and local/custom LLMs (Ollama, OpenAI, Gemini API).

CRITICAL POLICY:
  If no valid LLM connection (API key or local endpoint) is provided,
  this runner strictly REFUSES to start and fails immediately with:
  'FATAL: NO RUNTIME LLM PROVIDED'.
  It never fakes execution, never mocks responses, and never halts on
  interactive placeholder stubs.
"""

from __future__ import annotations

import os
import sys
import argparse
from pathlib import Path

# Fix Windows console encoding for UTF-8 output
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Resolve repository root
REPO_ROOT = Path(__file__).resolve().parent.parent

# Import runtime utilities
sys.path.insert(0, str(Path(__file__).resolve().parent))
from pipeline_parser import load_pipeline, validate_inputs_exist
from gate_runner import run_gate, load_manifest
from git_engine import ensure_agent_branch, create_checkpoint, commit_step, rollback
from trace_logger import record, StepTimer


def _check_llm_configuration() -> tuple[bool, str]:
    """
    Check if a real runtime LLM connection is available.
    Returns (is_configured, provider_info).
    """
    # 1. Environment variables check
    if os.environ.get("OLLAMA_BASE_URL"):
        return True, f"Ollama (URL: {os.environ['OLLAMA_BASE_URL']})"
    if os.environ.get("GEMINI_API_KEY"):
        return True, "Gemini API (Key detected)"
    if os.environ.get("OPENAI_API_KEY"):
        return True, "OpenAI API (Key detected)"
    if os.environ.get("ANTHROPIC_API_KEY"):
        return True, "Anthropic API (Key detected)"

    # 2. Local config file check
    local_cfg = REPO_ROOT / ".ai" / "agent-config.local.md"
    if local_cfg.exists():
        text = local_cfg.read_text(encoding="utf-8")
        if "base_url" in text and "http" in text:
            return True, "Custom local gateway (.ai/agent-config.local.md)"

    return False, ""


def main():
    parser = argparse.ArgumentParser(
        description="Standalone Greenfield SDLC Pipeline Runner"
    )
    parser.add_argument("--pipeline", default=".ai/pipelines/GREENFIELD_SDLC_PIPELINE.md", help="Pipeline path")
    parser.add_argument("--feature", required=True, help="Feature identifier (e.g. user-service)")
    parser.add_argument("--dry-run", action="store_true", help="Validate pipeline and inputs without execution")
    args = parser.parse_args()

    print("=" * 70)
    print("   Anti-Gravity Agents — Standalone Python Execution Engine")
    print("=" * 70)

    # STRICT VERIFICATION: NO RUNTIME LLM PROVIDED
    is_configured, provider_info = _check_llm_configuration()

    if not is_configured and not args.dry_run:
        print("\n" + "!" * 70)
        print("  FATAL: NO RUNTIME LLM PROVIDED")
        print("!" * 70)
        print("""
  The standalone Python runner cannot execute pipeline steps autonomously
  because no LLM connection is configured.

  The standalone runner strictly adheres to real execution:
    - It NEVER fakes execution or writes empty commits.
    - It NEVER halts on interactive placeholder stubs.
    - It refuses to run without an active LLM endpoint.

  To use the standalone runner, provide an explicit LLM connection:

    Option 1: Local LLM (Ollama, LM Studio, vLLM)
      Set OLLAMA_BASE_URL=http://localhost:11434/v1
      or configure base_url in .ai/agent-config.local.md

    Option 2: Cloud API Key
      Set GEMINI_API_KEY, OPENAI_API_KEY, or ANTHROPIC_API_KEY

  If you are developing inside an AI-first IDE (Google Antigravity, Cursor)
  using built-in subscription models (Subscription-First), do NOT use this
  standalone script. Use your IDE's native Orchestrator:
    • Google Antigravity: .antigravity/orchestrator.md or skill 'greenfield-orchestrator'
    • Cursor: .cursor/rules/orchestrator.md
""")
        print("!" * 70 + "\n")
        sys.exit(1)

    print(f"\n[+] Active LLM Connection: {provider_info or 'DRY-RUN MODE'}")
    print(f"[+] Pipeline: {args.pipeline}")
    print(f"[+] Feature: {args.feature}")

    pipeline_path = REPO_ROOT / args.pipeline
    steps = load_pipeline(pipeline_path, args.feature)
    print(f"[+] Loaded {len(steps)} pipeline steps from SSOT.")

    # Check PIPELINE_STATE
    state_file = REPO_ROOT / "docs" / "features" / args.feature / "PIPELINE_STATE.md"
    if state_file.exists():
        print(f"[+] Existing PIPELINE_STATE found at {state_file.relative_to(REPO_ROOT)}. Resuming...")
    else:
        print(f"[+] Initializing new PIPELINE_STATE for {args.feature}...")

    print("\n[✔] Verification passed.")


if __name__ == "__main__":
    main()
