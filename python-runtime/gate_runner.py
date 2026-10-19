"""
gate_runner.py — Quality Gate Runner & TDD Validator
=====================================================
Parses the "Execution & Verification Commands" manifest from docs/PROJECT_ENV.md
and executes the correct verification command for each pipeline step's Gate type.

Gate types:
  NONE                 — No automated check; always passes.
  COMPILE_CHECK        — Build/compile command must exit 0.
  UNIT_RED_PHASE       — Compile must succeed; tests must fail ONLY on assertions.
  UNIT_GREEN_PHASE     — Compile + all unit tests must pass (exit 0).
  INTEGRATION_GREEN_PHASE — Integration/E2E tests must pass (exit 0).

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import re
import subprocess
import dataclasses
from pathlib import Path
from typing import Optional


# ---------------------------------------------------------------------------
# Data Models
# ---------------------------------------------------------------------------

@dataclasses.dataclass
class GateResult:
    """Result of a Quality Gate check."""
    passed: bool
    gate: str
    output: str            # combined stdout + stderr
    error_summary: str     # brief human-readable failure reason


@dataclasses.dataclass
class CommandManifest:
    """Commands extracted from docs/PROJECT_ENV.md."""
    compile_check: str
    unit_test: str
    integration_test: str
    run_single_test: str


# ---------------------------------------------------------------------------
# TDD Red-Phase failure classifiers
# ---------------------------------------------------------------------------

# Patterns that indicate a test failed due to missing implementation (OK for Red Phase)
_RED_PHASE_OK_PATTERNS: list[re.Pattern] = [
    re.compile(r"UnsupportedOperationException", re.IGNORECASE),
    re.compile(r"Not\s+[Ii]mplemented", re.IGNORECASE),
    re.compile(r"NotImplementedError", re.IGNORECASE),
    re.compile(r"AssertionError", re.IGNORECASE),
    re.compile(r"AssertionFailedError", re.IGNORECASE),
    re.compile(r"Expected.*but was", re.IGNORECASE),
    re.compile(r"expected:<.*> but was:<", re.IGNORECASE),
    re.compile(r"org\.opentest4j\.AssertionFailedError", re.IGNORECASE),
    re.compile(r"org\.junit\..*AssertionError", re.IGNORECASE),
    re.compile(r"assert.*failed", re.IGNORECASE),
]

# Patterns that indicate a BAD failure (compile error, harness crash)
_RED_PHASE_BAD_PATTERNS: list[re.Pattern] = [
    re.compile(r"COMPILATION ERROR", re.IGNORECASE),
    re.compile(r"error:\s+cannot find symbol", re.IGNORECASE),
    re.compile(r"error:\s+package .+ does not exist", re.IGNORECASE),
    re.compile(r"NullPointerException", re.IGNORECASE),
    re.compile(r"ClassNotFoundException", re.IGNORECASE),
    re.compile(r"NoClassDefFoundError", re.IGNORECASE),
    re.compile(r"BUILD FAILED.*COMPILATION", re.IGNORECASE),
    re.compile(r"Process 'command' finished with non-zero exit value", re.IGNORECASE),
]

# Patterns indicating that tests actually ran and all passed
_ALL_TESTS_PASSED_PATTERNS: list[re.Pattern] = [
    re.compile(r"BUILD SUCCESS", re.IGNORECASE),
    re.compile(r"Tests run: \d+, Failures: 0, Errors: 0", re.IGNORECASE),
    re.compile(r"\d+ tests? passed", re.IGNORECASE),
    re.compile(r"OK \(\d+ tests?\)", re.IGNORECASE),
    re.compile(r"All tests passed", re.IGNORECASE),
]


# ---------------------------------------------------------------------------
# PROJECT_ENV.md parser
# ---------------------------------------------------------------------------

# Section heading patterns
_SECTION_MARKERS = {
    "compile_check":     [
        re.compile(r"compile\s*/\s*build\s*check", re.IGNORECASE),
        re.compile(r"compile\s+check", re.IGNORECASE),
    ],
    "unit_test":         [
        re.compile(r"run\s+all\s+tests", re.IGNORECASE),
        re.compile(r"unit\s*[/&]\s*slice\s+test", re.IGNORECASE),
        re.compile(r"unit\s+test", re.IGNORECASE),
    ],
    "integration_test":  [
        re.compile(r"integration\s*/\s*e2e\s+test", re.IGNORECASE),
        re.compile(r"integration\s+test", re.IGNORECASE),
    ],
    "run_single_test":   [
        re.compile(r"run\s+single\s+test", re.IGNORECASE),
    ],
}


def _match_section(line: str, patterns: list[re.Pattern]) -> bool:
    for p in patterns:
        if p.search(line):
            return True
    return False


def parse_project_env(env_file: Path) -> CommandManifest:
    """
    Parse commands from docs/PROJECT_ENV.md.
    """
    if not env_file.exists():
        raise FileNotFoundError(
            f"Project environment manifest not found: {env_file}. "
            "Run the Architect agent (Step 01) first to generate it."
        )

    text = env_file.read_text(encoding="utf-8")
    lines = text.splitlines()

    commands: dict[str, str] = {k: "" for k in _SECTION_MARKERS}
    current_key: Optional[str] = None
    in_code_block = False

    for line in lines:
        stripped = line.strip()

        # Detect code fence open/close
        if stripped.startswith("```") and not in_code_block:
            in_code_block = True
            continue
        if stripped == "```" and in_code_block:
            in_code_block = False
            if current_key and commands[current_key]:
                current_key = None
            continue

        if in_code_block:
            if stripped and current_key and not commands[current_key]:
                commands[current_key] = stripped
            continue

        for key, patterns in _SECTION_MARKERS.items():
            if not commands[key] and _match_section(stripped, patterns):
                current_key = key
                inline = re.findall(r"`([^`]+)`", stripped)
                if inline:
                    commands[current_key] = max(inline, key=len)
                    current_key = None
                break
        else:
            if current_key:
                inline = re.findall(r"`([^`]+)`", stripped)
                if inline and not commands[current_key]:
                    commands[current_key] = max(inline, key=len)
                    current_key = None

    if not commands["compile_check"]:
        raise ValueError(
            f"Could not extract 'Compile / Build Check' command from {env_file}. "
            "Ensure the manifest contains a section with that heading followed by a command."
        )
    if not commands["unit_test"]:
        raise ValueError(
            f"Could not extract 'Run All Tests' / 'Unit / Slice Test' command from {env_file}."
        )

    return CommandManifest(
        compile_check=commands["compile_check"],
        unit_test=commands["unit_test"],
        integration_test=commands["integration_test"] or commands["unit_test"],
        run_single_test=commands["run_single_test"] or commands["unit_test"],
    )


# ---------------------------------------------------------------------------
# Command runner
# ---------------------------------------------------------------------------

def _run_command(cmd: str, cwd: Path, timeout: int = 600) -> tuple[int, str]:
    try:
        import os
        run_cmd = cmd
        if os.name == "nt" and run_cmd.startswith("./"):
            run_cmd = ".\\" + run_cmd[2:]
        result = subprocess.run(
            run_cmd,
            shell=True,
            cwd=str(cwd),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
        )
        output = result.stdout.decode("utf-8", errors="replace")
        return result.returncode, output
    except subprocess.TimeoutExpired:
        return -1, f"Command timed out after {timeout}s: {cmd}"
    except Exception as exc:
        return -1, f"Failed to run command '{cmd}': {exc}"


# ---------------------------------------------------------------------------
# Gate implementations
# ---------------------------------------------------------------------------

def _gate_none(_manifest: CommandManifest, _cwd: Path) -> GateResult:
    return GateResult(passed=True, gate="NONE", output="", error_summary="")


def _gate_compile_check(manifest: CommandManifest, cwd: Path) -> GateResult:
    code, output = _run_command(manifest.compile_check, cwd)
    if code == 0:
        return GateResult(passed=True, gate="COMPILE_CHECK", output=output, error_summary="")
    return GateResult(
        passed=False, gate="COMPILE_CHECK", output=output,
        error_summary=f"Compilation failed (exit code {code})."
    )


def _gate_unit_red_phase(manifest: CommandManifest, cwd: Path) -> GateResult:
    # Stage 1: compile
    code, output = _run_command(manifest.compile_check, cwd)
    if code != 0:
        return GateResult(
            passed=False, gate="UNIT_RED_PHASE", output=output,
            error_summary=(
                "UNIT_RED_PHASE failed: compilation errors detected. "
                "Tests must compile cleanly for the Red Phase to be valid."
            ),
        )

    # Stage 2: run tests — expect non-zero exit code
    code, output = _run_command(manifest.unit_test, cwd)
    if code == 0:
        return GateResult(
            passed=False, gate="UNIT_RED_PHASE", output=output,
            error_summary=(
                "UNIT_RED_PHASE failed: all tests passed, but Red Phase requires "
                "tests to FAIL. The test-writer agent must write failing tests first."
            ),
        )

    has_ok_failure = any(p.search(output) for p in _RED_PHASE_OK_PATTERNS)
    has_bad_failure = any(p.search(output) for p in _RED_PHASE_BAD_PATTERNS)

    if has_bad_failure and not has_ok_failure:
        return GateResult(
            passed=False, gate="UNIT_RED_PHASE", output=output,
            error_summary=(
                "UNIT_RED_PHASE failed: tests failed due to a BAD reason "
                "(NullPointerException, ClassNotFoundException, or compile error), "
                "not due to UnsupportedOperationException / AssertionError. "
                "Fix mock pre-configuration in the test-writer output."
            ),
        )

    return GateResult(passed=True, gate="UNIT_RED_PHASE", output=output, error_summary="")


def _gate_unit_green_phase(manifest: CommandManifest, cwd: Path) -> GateResult:
    code, output = _run_command(manifest.unit_test, cwd)
    if code == 0 and any(p.search(output) for p in _ALL_TESTS_PASSED_PATTERNS):
        return GateResult(passed=True, gate="UNIT_GREEN_PHASE", output=output, error_summary="")
    if code == 0:
        return GateResult(passed=True, gate="UNIT_GREEN_PHASE", output=output, error_summary="")
    return GateResult(
        passed=False, gate="UNIT_GREEN_PHASE", output=output,
        error_summary=f"Unit tests failed (exit code {code})."
    )


def _gate_integration_green_phase(manifest: CommandManifest, cwd: Path) -> GateResult:
    code, output = _run_command(manifest.integration_test, cwd, timeout=900)
    if code == 0:
        return GateResult(passed=True, gate="INTEGRATION_GREEN_PHASE", output=output, error_summary="")
    return GateResult(
        passed=False, gate="INTEGRATION_GREEN_PHASE", output=output,
        error_summary=f"Integration/E2E tests failed (exit code {code})."
    )


_GATE_DISPATCH = {
    "NONE":                      _gate_none,
    "COMPILE_CHECK":             _gate_compile_check,
    "UNIT_RED_PHASE":            _gate_unit_red_phase,
    "UNIT_GREEN_PHASE":          _gate_unit_green_phase,
    "INTEGRATION_GREEN_PHASE":   _gate_integration_green_phase,
}


def run_gate(
    gate: str,
    manifest: CommandManifest,
    repo_root: Path,
) -> GateResult:
    gate_upper = gate.strip().upper()
    fn = _GATE_DISPATCH.get(gate_upper)
    if fn is None:
        return GateResult(
            passed=False, gate=gate,
            output="",
            error_summary=(
                f"Unknown gate type '{gate}'. "
                f"Valid values: {list(_GATE_DISPATCH.keys())}"
            ),
        )
    return fn(manifest, repo_root)


def load_manifest(repo_root: Path) -> CommandManifest:
    for candidate in [
        repo_root / "docs" / "PROJECT_ENV.md",
        repo_root / "PROJECT_ENV.md",
    ]:
        if candidate.exists():
            return parse_project_env(candidate)
    raise FileNotFoundError(
        f"Project environment manifest 'PROJECT_ENV.md' not found under "
        f"{repo_root / 'docs'} or {repo_root}. "
        "Run the Architect agent (Step 01) to generate it."
    )


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 3:
        print("Usage: python gate_runner.py <repo_root> <gate_type>")
        print(f"  gate_type: {list(_GATE_DISPATCH.keys())}")
        sys.exit(1)

    root = Path(sys.argv[1])
    gate_type = sys.argv[2]
    env_manifest = load_manifest(root)
    result = run_gate(gate_type, env_manifest, root)
    print(f"Gate: {result.gate}")
    print(f"Passed: {result.passed}")
    if not result.passed:
        print(f"Error: {result.error_summary}")
    sys.exit(0 if result.passed else 1)
