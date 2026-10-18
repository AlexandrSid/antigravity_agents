"""
orchestrator.py — Main CLI Entry Point & Pipeline Coordinator
=============================================================
Ties all subsystems together into a single, runnable orchestrator:

  pipeline_parser  — reads .ai/GREENFIELD_SDLC_PIPELINE.md
  config_resolver  — detects platform and resolves model profiles
  tool_shell       — bounded, scope-enforced tool execution for agents
  gate_runner      — Quality Gate (COMPILE_CHECK / UNIT_RED_PHASE / …)
  git_engine       — checkpoints, branch protection, rollback, commits
  trace_logger     — EXECUTION_TRACE.json per step

Usage:
  python .ai/scripts/orchestrator.py \\
      --pipeline .ai/GREENFIELD_SDLC_PIPELINE.md \\
      --feature <feature_name> \\
      [--mode auto|interactive] \\
      [--git-auto] \\
      [--from-step N] \\
      [--dry-run]

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import argparse
import json
import sys
import textwrap
from pathlib import Path

# ---------------------------------------------------------------------------
# Resolve repo root (the directory containing the .ai/ folder)
# ---------------------------------------------------------------------------
_SCRIPT_DIR = Path(__file__).resolve().parent          # .ai/scripts/
_AI_DIR     = _SCRIPT_DIR.parent                       # .ai/
REPO_ROOT   = _AI_DIR.parent                           # repository root

# Add .ai/scripts to sys.path so sibling modules can be imported
sys.path.insert(0, str(_SCRIPT_DIR))

from pipeline_parser import load_pipeline, validate_inputs_exist, PipelineStep
from config_resolver  import load_config, print_config_summary, OrchestratorConfig
from tool_shell       import create_session, dispatch_tool, TOOL_SCHEMAS
from gate_runner      import load_manifest, run_gate, CommandManifest
from git_engine       import (
    ensure_agent_branch, create_checkpoint, rollback, commit_step,
    GitCheckpoint,
)
from trace_logger     import StepTimer, record, print_trace_summary, session_totals


# ---------------------------------------------------------------------------
# ANSI colours (degrade gracefully on terminals without support)
# ---------------------------------------------------------------------------
try:
    import os
    _NO_COLOR = os.environ.get("NO_COLOR") or not sys.stdout.isatty()
except Exception:
    _NO_COLOR = True


def _c(text: str, code: str) -> str:
    if _NO_COLOR:
        return text
    return f"\033[{code}m{text}\033[0m"


def _green(t: str)  -> str: return _c(t, "32")
def _red(t: str)    -> str: return _c(t, "31")
def _yellow(t: str) -> str: return _c(t, "33")
def _bold(t: str)   -> str: return _c(t, "1")
def _cyan(t: str)   -> str: return _c(t, "36")


# ---------------------------------------------------------------------------
# Banner
# ---------------------------------------------------------------------------

BANNER = r"""
╔══════════════════════════════════════════════════════════╗
║        Anti-Gravity Agents — CLI Orchestrator           ║
║        Subscription-First · Zero External Dependencies  ║
╚══════════════════════════════════════════════════════════╝
"""


def _print_banner(args: argparse.Namespace, config: OrchestratorConfig) -> None:
    print(_bold(BANNER))
    print(f"  Pipeline : {args.pipeline}")
    print(f"  Feature  : {args.feature}")
    print(f"  Mode     : {args.mode}")
    print(f"  Git Auto : {args.git_auto}")
    if args.from_step:
        print(f"  From step: {args.from_step:02d}")
    if args.dry_run:
        print(_yellow("  DRY RUN  : no LLM calls, no file writes, no git ops"))
    print()
    print(_bold("  LLM Configuration:"))
    print_config_summary(config)
    print()


# ---------------------------------------------------------------------------
# Stub: native agent dispatch
# ---------------------------------------------------------------------------

def _dispatch_to_native_agent(
    step: PipelineStep,
    config: OrchestratorConfig,
    feature: str,
    repo_root: Path,
    error_context: str = "",
) -> tuple[int, int, str]:
    """
    Dispatch a pipeline step to the native IDE agent (Subscription-First).

    This stub builds the full context capsule (system role + input artifacts +
    task prompt) and writes it to .ai/scripts/.current_step_context.md for
    the native platform agent to pick up programmatically.

    In the reference implementation this function is the integration point
    where platform-specific SDK calls (e.g. google-antigravity SDK, agy CLI)
    are wired in. The capsule file format is stable and documented below.

    Returns:
        (prompt_tokens_estimate, completion_tokens_estimate, agent_summary)
    """
    profile = config.get_profile(step.profile)

    # --- Build context capsule ---
    system_role_path = REPO_ROOT / step.rule_file
    system_role = system_role_path.read_text(encoding="utf-8") if system_role_path.exists() else \
        f"[MISSING RULE FILE: {step.rule_file}]"

    input_contents: list[str] = []
    for inp_path in step.inputs:
        full = repo_root / inp_path
        if full.exists() and full.is_file():
            input_contents.append(f"### {inp_path}\n\n{full.read_text(encoding='utf-8')}")
        else:
            input_contents.append(f"### {inp_path}\n\n[FILE NOT FOUND]")

    task_prompt = _build_task_prompt(step, feature, error_context)

    capsule = "\n\n---\n\n".join([
        f"# SYSTEM ROLE\n\n{system_role}",
        f"# INPUT ARTIFACTS\n\n" + "\n\n".join(input_contents),
        f"# TASK\n\n{task_prompt}",
        f"# AVAILABLE TOOLS (JSON Schema)\n\n```json\n{json.dumps(TOOL_SCHEMAS, indent=2)}\n```",
    ])

    capsule_path = _SCRIPT_DIR / ".current_step_context.md"
    capsule_path.write_text(capsule, encoding="utf-8")

    print(_cyan(
        f"  Context capsule written → {capsule_path.relative_to(repo_root)}\n"
        f"  Provider: {profile.provider} | Model: {profile.preferred_model}\n"
        f"  The native platform agent should process this capsule automatically."
    ))

    # Estimate token counts from capsule size (rough: 4 chars ≈ 1 token)
    prompt_tokens = len(capsule) // 4
    # For native/local providers completion estimate is notional
    completion_tokens = 1024  # placeholder

    # In a fully automated environment, insert SDK call here and
    # return actual token counts and the agent's summary string.
    # For now, prompt the operator to confirm completion in interactive mode.
    agent_summary = f"Step {step.step_id} ({step.role_name}) — awaiting native agent completion."
    return prompt_tokens, completion_tokens, agent_summary


def _build_task_prompt(step: PipelineStep, feature: str, error_context: str) -> str:
    prompt = textwrap.dedent(f"""
        You are executing **Step {step.step_id}: {step.role_name}** of the Greenfield SDLC pipeline.

        Feature under development: `{feature}`

        Your task is described in your system role above. Produce all required output
        artifacts listed below and signal completion by calling `complete_step(summary)`.

        Expected outputs:
        {chr(10).join(f"  - {o}" for o in step.outputs)}

        Gate that will be run after your completion: `{step.gate}`
    """).strip()

    if error_context:
        prompt += f"\n\n---\n\n**⚠ PREVIOUS ATTEMPT FAILED — Fix the following:**\n\n{error_context}"

    return prompt


# ---------------------------------------------------------------------------
# Self-correction loop
# ---------------------------------------------------------------------------

MAX_RETRIES = 3


def _run_step_with_retries(
    step: PipelineStep,
    config: OrchestratorConfig,
    manifest: CommandManifest,
    feature: str,
    repo_root: Path,
    dry_run: bool,
    interactive: bool,
) -> bool:
    """
    Execute one pipeline step with up to MAX_RETRIES self-correction attempts.
    Returns True if the step ultimately succeeded.
    """
    trace_file = repo_root / "docs" / "features" / feature / "EXECUTION_TRACE.json"
    prof = config.get_profile(step.profile)

    for attempt in range(1, MAX_RETRIES + 1):
        print(_bold(f"\n  ▶ Step {step.step_id} — {step.role_name}  "
                    f"[{step.profile}]  Gate: {step.gate}  "
                    f"(attempt {attempt}/{MAX_RETRIES})"))

        # 1. Git checkpoint
        checkpoint: GitCheckpoint | None = None
        if not dry_run:
            checkpoint = create_checkpoint(repo_root, step.step_id)
            print(f"  📌 Checkpoint: {checkpoint.sha[:8]}")

        # 2. Validate input artifacts
        missing = validate_inputs_exist(step, repo_root)
        if missing:
            print(_red(f"  ✖ Missing input artifacts: {missing}"))
            print(_red("  Cannot start step — aborting self-correction loop."))
            return False

        # 3. (Interactive) confirm before running
        if interactive and attempt == 1:
            ans = _prompt(f"  ▸ Proceed with Step {step.step_id}? [Y/n/skip]: ").strip().lower()
            if ans in ("n", "no"):
                print("  Skipped by operator.")
                return False
            if ans == "skip":
                return True  # treat skip as success so pipeline continues

        # 4. Dispatch to native agent
        timer = StepTimer().start()
        error_context = ""  # populated only on retry

        if not dry_run:
            prompt_tokens, completion_tokens, summary = _dispatch_to_native_agent(
                step, config, feature, repo_root, error_context
            )

            # In interactive/native mode: wait for operator confirmation
            if interactive or prof.provider == "native":
                input(_yellow(
                    f"\n  ⏸  Native agent is working on Step {step.step_id}.\n"
                    f"  Press Enter once the agent has completed its task ..."
                ))
        else:
            prompt_tokens, completion_tokens, summary = 0, 0, "DRY RUN"
            print(_yellow("  [DRY RUN] Skipping agent dispatch."))

        duration = timer.stop()

        # 5. Run Quality Gate
        print(f"  🔍 Running gate: {step.gate} …")
        if not dry_run and step.gate != "NONE":
            gate_result = run_gate(step.gate, manifest, repo_root)
        else:
            from gate_runner import GateResult
            gate_result = GateResult(passed=True, gate=step.gate, output="", error_summary="")

        # 6. Record trace entry
        exit_status = "SUCCESS" if gate_result.passed else f"RETRY_{attempt}"
        if not dry_run:
            entry = record(
                trace_file=trace_file,
                step_id=step.step_id,
                agent_role=step.role_name,
                model_used=prof.preferred_model,
                provider=prof.provider,
                timer=timer,
                prompt_tokens=prompt_tokens,
                completion_tokens=completion_tokens,
                exit_status=exit_status,
            )
            print_trace_summary(entry)

        # 7. Gate passed → commit and return success
        if gate_result.passed:
            print(_green(f"  ✔ Gate {step.gate} PASSED"))
            if not dry_run:
                commit_res = commit_step(repo_root, step.step_id, step.role_name, summary)
                if commit_res.ok:
                    print(_green(f"  ✔ {commit_res.output}"))
                else:
                    print(_yellow(f"  ⚠ Commit warning: {commit_res.error}"))
            return True

        # 8. Gate failed
        print(_red(f"  ✖ Gate {step.gate} FAILED: {gate_result.error_summary}"))
        if gate_result.output:
            # Print last 40 lines of build output for context
            lines = gate_result.output.strip().splitlines()
            snippet = "\n".join(lines[-40:])
            print(_yellow(f"\n--- Build output (last 40 lines) ---\n{snippet}\n---"))

        error_context = f"{gate_result.error_summary}\n\n{gate_result.output[-3000:]}"

        if attempt < MAX_RETRIES:
            print(_yellow(f"  ↩ Rolling back to checkpoint {checkpoint.sha[:8]} and retrying …"))
            if not dry_run and checkpoint:
                rb = rollback(repo_root, checkpoint)
                print(f"  {rb.output or rb.error}")
        # end for-attempt loop

    # All retries exhausted
    return False


# ---------------------------------------------------------------------------
# Human-in-the-Loop fallback
# ---------------------------------------------------------------------------

def _human_in_the_loop(step: PipelineStep) -> str:
    """
    Display the HITL menu and return the chosen action:
    "retry_ide", "retry_elevated", "abort", "skip"
    """
    print(_bold(_red(
        f"\n  ⛔ Step {step.step_id} ({step.role_name}) FAILED after {MAX_RETRIES} attempts."
    )))
    print(_yellow(textwrap.dedent("""
  Human-in-the-Loop — choose an action:
    [1] Retry manually in IDE, then press Enter to resume pipeline
    [2] Force retry with an elevated reasoning model
    [3] Skip this step and continue (DANGEROUS — may break downstream steps)
    [4] Abort pipeline execution
    """)))
    choice = _prompt("  Your choice [1/2/3/4]: ").strip()
    mapping = {"1": "retry_ide", "2": "retry_elevated", "3": "skip", "4": "abort"}
    return mapping.get(choice, "abort")


def _prompt(message: str) -> str:
    try:
        return input(message)
    except (EOFError, KeyboardInterrupt):
        print("\n  Interrupted.")
        return ""


# ---------------------------------------------------------------------------
# Main pipeline loop
# ---------------------------------------------------------------------------

def run_pipeline(args: argparse.Namespace) -> int:
    """
    Execute the full pipeline. Returns process exit code (0=success, 1=failure).
    """
    repo_root = REPO_ROOT

    # --- Load configuration ---
    try:
        config = load_config(repo_root)
    except Exception as exc:
        print(_red(f"  ✖ Configuration error: {exc}"))
        return 1

    _print_banner(args, config)

    # --- Branch protection ---
    if not args.dry_run:
        br = ensure_agent_branch(repo_root, args.feature, git_auto=args.git_auto)
        if not br.ok:
            print(_red(f"\n  ✖ {br.error}"))
            return 1
        print(_green(f"  ✔ {br.output}"))

    # --- Load pipeline ---
    try:
        steps = load_pipeline(args.pipeline, args.feature)
    except Exception as exc:
        print(_red(f"  ✖ Pipeline parse error: {exc}"))
        return 1

    # --- Load PROJECT_ENV.md manifest ---
    try:
        manifest = load_manifest(repo_root)
        print(_green(f"  ✔ Loaded command manifest from PROJECT_ENV.md"))
        print(f"       compile  : {manifest.compile_check}")
        print(f"       unit     : {manifest.unit_test}")
        print(f"       integ.   : {manifest.integration_test}")
        print()
    except FileNotFoundError as exc:
        # Not fatal if pipeline only has NONE gates (early phases)
        manifest = None
        print(_yellow(f"  ⚠ {exc}\n    Gates other than NONE will fail until PROJECT_ENV.md exists."))

    # --- Filter steps (--from-step) ---
    if args.from_step:
        steps = [s for s in steps if int(s.step_id) >= args.from_step]
        if not steps:
            print(_yellow(f"  No steps at or after step {args.from_step:02d}."))
            return 0

    print(_bold(f"  {len(steps)} step(s) to execute: "
                + ", ".join(f"Step {s.step_id}" for s in steps)))
    print()

    # --- Execute each step ---
    from gate_runner import CommandManifest as CM
    dummy_manifest = CM(
        compile_check="echo NOOP",
        unit_test="echo NOOP",
        integration_test="echo NOOP",
        run_single_test="echo NOOP",
    )

    for step in steps:
        active_manifest = manifest if manifest else dummy_manifest

        success = _run_step_with_retries(
            step=step,
            config=config,
            manifest=active_manifest,
            feature=args.feature,
            repo_root=repo_root,
            dry_run=args.dry_run,
            interactive=(args.mode == "interactive"),
        )

        if not success:
            action = _human_in_the_loop(step)

            if action == "retry_ide":
                print(_yellow("\n  ⏸  Resuming after manual IDE fix …"))
                input("  Press Enter when ready …")
                # Re-run gate only
                if manifest and step.gate != "NONE":
                    gr = run_gate(step.gate, active_manifest, repo_root)
                    if gr.passed:
                        print(_green("  ✔ Gate passed after manual fix."))
                        commit_step(repo_root, step.step_id, step.role_name, "Manual fix applied.")
                        continue
                    else:
                        print(_red(f"  ✖ Gate still failing: {gr.error_summary}"))
                        return 1

            elif action == "retry_elevated":
                print(_yellow("  Retrying with elevated model — adjust agent-config.local.md if needed."))
                # Run the step one more time with the current config
                success = _run_step_with_retries(
                    step=step, config=config, manifest=active_manifest,
                    feature=args.feature, repo_root=repo_root,
                    dry_run=args.dry_run, interactive=True,
                )
                if not success:
                    print(_red(f"  ✖ Step {step.step_id} still failed. Aborting."))
                    return 1

            elif action == "skip":
                print(_yellow(f"  ⚠ Skipping Step {step.step_id}. Downstream steps may fail."))
                continue

            else:  # abort
                print(_red("  Pipeline aborted by operator."))
                return 1

    # --- Final summary ---
    trace_file = repo_root / "docs" / "features" / args.feature / "EXECUTION_TRACE.json"
    totals = session_totals(trace_file)
    print(_bold("\n" + "═" * 60))
    print(_bold(_green("  ✔ Pipeline completed successfully!")))
    print(f"  Steps completed : {totals['steps']}")
    print(f"  Total tokens    : {totals['total_tokens']:,}")
    cost = totals['total_cost_usd']
    cost_str = f"${cost:.4f}" if cost > 0 else "$0.00 (native/local)"
    print(f"  Total cost      : {cost_str}")
    print(f"  Total time      : {totals['total_duration_sec']}s")
    print(_bold("═" * 60 + "\n"))
    return 0


# ---------------------------------------------------------------------------
# Argument parser
# ---------------------------------------------------------------------------

def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="orchestrator",
        description="Anti-Gravity Agents — Greenfield SDLC CLI Orchestrator",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""
        Examples:
          # Full auto run
          python .ai/scripts/orchestrator.py \\
              --pipeline .ai/GREENFIELD_SDLC_PIPELINE.md \\
              --feature user-service \\
              --mode auto --git-auto

          # Interactive step-by-step with manual git branch
          python .ai/scripts/orchestrator.py \\
              --pipeline .ai/GREENFIELD_SDLC_PIPELINE.md \\
              --feature auth --mode interactive

          # Dry run to validate pipeline parsing without side effects
          python .ai/scripts/orchestrator.py \\
              --pipeline .ai/GREENFIELD_SDLC_PIPELINE.md \\
              --feature demo --dry-run

          # Resume from step 5 (e.g. after a manual fix)
          python .ai/scripts/orchestrator.py \\
              --pipeline .ai/GREENFIELD_SDLC_PIPELINE.md \\
              --feature user-service --from-step 5
        """),
    )
    parser.add_argument(
        "--pipeline", default=".ai/GREENFIELD_SDLC_PIPELINE.md",
        help="Path to the pipeline Markdown file (default: .ai/GREENFIELD_SDLC_PIPELINE.md).",
    )
    parser.add_argument(
        "--feature", required=True,
        help="Feature name; resolves {feature} placeholders in artifact paths.",
    )
    parser.add_argument(
        "--mode", choices=["auto", "interactive"], default="auto",
        help="Execution mode: 'auto' (unattended) or 'interactive' (confirm each step).",
    )
    parser.add_argument(
        "--git-auto", action="store_true",
        help="Automatically create and switch to an agent-feature-{feature} branch if on a protected branch.",
    )
    parser.add_argument(
        "--from-step", type=int, default=None, metavar="N",
        help="Start execution from step N (useful for resuming after a manual fix).",
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Parse and validate the pipeline without making LLM calls, file writes, or git operations.",
    )
    return parser


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = _build_parser()
    args = parser.parse_args()

    # Resolve pipeline path relative to repo root if not absolute
    if not Path(args.pipeline).is_absolute():
        args.pipeline = str(REPO_ROOT / args.pipeline)

    sys.exit(run_pipeline(args))


if __name__ == "__main__":
    main()
