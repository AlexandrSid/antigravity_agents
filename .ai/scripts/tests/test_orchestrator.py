"""
test_orchestrator.py — Unit Tests for Orchestrator Core Subsystems
===================================================================
Tests all orchestrator modules without making real LLM calls,
file writes beyond a temp directory, or network requests.

Run with the stdlib test runner (no pytest required):
  python -m unittest .ai/scripts/tests/test_orchestrator.py -v

Or with pytest if available:
  pytest .ai/scripts/tests/ -v
"""

from __future__ import annotations

import json
import shutil
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

# Make sibling scripts importable
_TESTS_DIR  = Path(__file__).resolve().parent
_SCRIPTS_DIR = _TESTS_DIR.parent
sys.path.insert(0, str(_SCRIPTS_DIR))

import pipeline_parser
import config_resolver
import tool_shell
import gate_runner
import git_engine
import trace_logger


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class TempRepo:
    """Creates a temporary directory that looks like a minimal repo."""

    def __enter__(self) -> "TempRepo":
        self.root = Path(tempfile.mkdtemp(prefix="agt_test_"))
        (self.root / ".ai").mkdir()
        (self.root / ".ai" / "rules").mkdir()
        (self.root / ".ai" / "scripts").mkdir()
        (self.root / "docs").mkdir()
        (self.root / "src").mkdir()
        return self

    def __exit__(self, *_) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    def write(self, rel_path: str, content: str) -> Path:
        p = self.root / rel_path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(textwrap.dedent(content), encoding="utf-8")
        return p


# ---------------------------------------------------------------------------
# Minimal pipeline Markdown fixture
# ---------------------------------------------------------------------------

_MINIMAL_PIPELINE_MD = """\
# Greenfield SDLC Pipeline

## 2. Execution Matrix

Шаги выполняются строго последовательно.

| Шаг | Роль и файл правил | Входные артефакты | Выходные артефакты | Профиль LLM | Gate | Назначение |
|:---:|---|---|---|:---:|:---:|---|
| **01** | **Architect**<br>`.ai/rules/architect.md` | `docs/PRD.md` | `docs/features/{feature}/SPEC.md` | `REASONING_HEAVY` | `NONE` | Проектирование |
| **04** | **Skeleton Writer**<br>`.ai/rules/skeleton-writer.md` | `docs/features/{feature}/SPEC.md` | `src/main/**` | `FAST_EXECUTION` | `COMPILE_CHECK` | Каркас |
| **05** | **Test Writer**<br>`.ai/rules/test-writer.md` | `docs/features/{feature}/TDD_PLAN.md` | `src/test/**` | `STRICT_CODEGEN` | `UNIT_RED_PHASE` | Тесты |
| **06** | **Code Writer**<br>`.ai/rules/code-writer.md` | `src/test/**` | `src/main/**` | `STRICT_CODEGEN` | `UNIT_GREEN_PHASE` | Реализация |
"""

_MINIMAL_AGENTS_CONFIG = """\
# Agent config

```
[REASONING_HEAVY]
provider = "native"
preferred_model = "native-environment-model"
fallback_model = "native-environment-model"
temperature = "0.2"
max_output_tokens = "8192"

[STRICT_CODEGEN]
provider = "native"
preferred_model = "native-environment-model"
fallback_model = "native-environment-model"
temperature = "0.1"
max_output_tokens = "8192"

[FAST_EXECUTION]
provider = "native"
preferred_model = "native-environment-model"
fallback_model = "native-environment-model"
temperature = "0.3"
max_output_tokens = "4096"
```
"""

_MINIMAL_AGENT_RULE = """\
### MANDATORY RESPONSE FORMAT
`**[test-agent]**:`

# Test Agent

## 2. ACCESS ZONES & STRICT LIMITATIONS

### Allowed Scope (WRITE Access):
- `src/test/**`
- `docs/features/{feature}/TDD_PLAN.md` (checkbox flip only)

### Read-Only Access:
- `src/main/**`
- `docs/features/{feature}/SPEC.md`

### Forbidden Scope (STRICTLY PROHIBITED):
- Strictly FORBIDDEN from modifying `src/main/**`
- Strictly FORBIDDEN from modifying `.ai/**`
"""

_MINIMAL_PROJECT_ENV = """\
# Local Project Environment & Commands

## Compile / Build Check

```
./gradlew testClasses
```

## Run All Tests

```
./gradlew test
```

## Unit / Slice Test

```
./gradlew test
```

## Integration / E2E Test

```
./gradlew integrationTest
```

## Run Single Test

```
./gradlew test --tests
```
"""


# ===========================================================================
# Test Suite: pipeline_parser
# ===========================================================================

class TestPipelineParser(unittest.TestCase):

    def setUp(self):
        self.tmp = TempRepo().__enter__()
        self.md_path = self.tmp.write(".ai/GREENFIELD_SDLC_PIPELINE.md", _MINIMAL_PIPELINE_MD)

    def tearDown(self):
        self.tmp.__exit__(None, None, None)

    def test_parses_correct_number_of_steps(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "user-service")
        self.assertEqual(len(steps), 4)

    def test_step_ids_are_sequential(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "user-service")
        ids = [s.step_id for s in steps]
        self.assertEqual(ids, ["01", "04", "05", "06"])

    def test_feature_interpolation(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "auth")
        step01 = steps[0]
        # inputs should contain resolved feature name
        resolved = [p for p in step01.inputs if "auth" in p]
        # step 01 input is docs/PRD.md which has no {feature}, check outputs
        self.assertIn("docs/features/auth/SPEC.md", step01.outputs)

    def test_gate_values_correct(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "x")
        gate_map = {s.step_id: s.gate for s in steps}
        self.assertEqual(gate_map["01"], "NONE")
        self.assertEqual(gate_map["04"], "COMPILE_CHECK")
        self.assertEqual(gate_map["05"], "UNIT_RED_PHASE")
        self.assertEqual(gate_map["06"], "UNIT_GREEN_PHASE")

    def test_profiles_correct(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "x")
        self.assertEqual(steps[0].profile, "REASONING_HEAVY")
        self.assertEqual(steps[1].profile, "FAST_EXECUTION")

    def test_rule_files_parsed(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "x")
        self.assertEqual(steps[0].rule_file, ".ai/rules/architect.md")

    def test_missing_file_raises(self):
        with self.assertRaises(FileNotFoundError):
            pipeline_parser.load_pipeline("/nonexistent/pipeline.md", "x")

    def test_missing_gate_column_raises(self):
        bad_md = _MINIMAL_PIPELINE_MD.replace("Gate | ", "")
        bad_path = self.tmp.write(".ai/BAD_PIPELINE.md", bad_md)
        with self.assertRaises(ValueError):
            pipeline_parser.load_pipeline(bad_path, "x")

    def test_validate_inputs_exist_finds_missing(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "user-service")
        step01 = steps[0]
        missing = pipeline_parser.validate_inputs_exist(step01, self.tmp.root)
        self.assertIn("docs/PRD.md", missing)

    def test_validate_inputs_exist_passes_when_file_present(self):
        steps = pipeline_parser.load_pipeline(self.md_path, "user-service")
        self.tmp.write("docs/PRD.md", "# PRD")
        step01 = steps[0]
        missing = pipeline_parser.validate_inputs_exist(step01, self.tmp.root)
        self.assertNotIn("docs/PRD.md", missing)


# ===========================================================================
# Test Suite: config_resolver
# ===========================================================================

class TestConfigResolver(unittest.TestCase):

    def setUp(self):
        self.tmp = TempRepo().__enter__()
        self.tmp.write(".ai/agents-config.md", _MINIMAL_AGENTS_CONFIG)

    def tearDown(self):
        self.tmp.__exit__(None, None, None)

    def test_loads_all_three_profiles(self):
        cfg = config_resolver.load_config(self.tmp.root)
        self.assertIn("REASONING_HEAVY", cfg.profiles)
        self.assertIn("STRICT_CODEGEN", cfg.profiles)
        self.assertIn("FAST_EXECUTION", cfg.profiles)

    def test_default_provider_is_native(self):
        cfg = config_resolver.load_config(self.tmp.root)
        for prof in cfg.profiles.values():
            self.assertEqual(prof.provider, "native")

    def test_local_override_wins(self):
        self.tmp.write(".ai/agent-config.local.md", textwrap.dedent("""
            ```
            [STRICT_CODEGEN]
            provider = "ollama"
            preferred_model = "qwen2.5-coder:7b"
            ```
        """))
        cfg = config_resolver.load_config(self.tmp.root)
        prof = cfg.profiles["STRICT_CODEGEN"]
        self.assertEqual(prof.provider, "ollama")
        self.assertEqual(prof.preferred_model, "qwen2.5-coder:7b")
        # Other profiles should remain native
        self.assertEqual(cfg.profiles["REASONING_HEAVY"].provider, "native")

    def test_antigravity_platform_detected(self):
        (self.tmp.root / ".antigravity").mkdir()
        cfg = config_resolver.load_config(self.tmp.root)
        self.assertEqual(cfg.platform, "antigravity")

    def test_cursor_platform_detected(self):
        (self.tmp.root / ".cursor").mkdir()
        cfg = config_resolver.load_config(self.tmp.root)
        self.assertEqual(cfg.platform, "cursor")

    def test_generic_platform_when_no_adapter(self):
        cfg = config_resolver.load_config(self.tmp.root)
        self.assertEqual(cfg.platform, "generic")

    def test_missing_primary_manifest_raises(self):
        (self.tmp.root / ".ai" / "agents-config.md").unlink()
        with self.assertRaises(FileNotFoundError):
            config_resolver.load_config(self.tmp.root)

    def test_get_profile_unknown_raises(self):
        cfg = config_resolver.load_config(self.tmp.root)
        with self.assertRaises(ValueError):
            cfg.get_profile("NONEXISTENT_PROFILE")


# ===========================================================================
# Test Suite: tool_shell
# ===========================================================================

class TestToolShell(unittest.TestCase):

    def setUp(self):
        self.tmp = TempRepo().__enter__()
        self.rule_path = self.tmp.write(".ai/rules/test-agent.md", _MINIMAL_AGENT_RULE)

    def tearDown(self):
        self.tmp.__exit__(None, None, None)

    def _make_session(self):
        return tool_shell.create_session("test-agent", self.rule_path, self.tmp.root)

    def test_write_scope_parsed(self):
        session = self._make_session()
        self.assertTrue(any("src/test" in p for p in session.allowed_write_globs))

    def test_forbidden_scope_parsed(self):
        session = self._make_session()
        self.assertTrue(any("src/main" in p or ".ai" in p for p in session.forbidden_globs))

    def test_read_artifact_existing_file(self):
        self.tmp.write("docs/features/x/SPEC.md", "# Spec content")
        session = self._make_session()
        result = tool_shell.tool_read_artifact(session, "docs/features/x/SPEC.md")
        self.assertTrue(result.ok)
        self.assertIn("Spec content", result.content)

    def test_read_artifact_missing_file(self):
        session = self._make_session()
        result = tool_shell.tool_read_artifact(session, "nonexistent.md")
        self.assertFalse(result.ok)
        self.assertIn("not found", result.error.lower())

    def test_apply_patch_within_scope(self):
        session = self._make_session()
        result = tool_shell.tool_apply_patch(session, "src/test/MyTest.java", "class MyTest {}")
        self.assertTrue(result.ok)
        written = (self.tmp.root / "src/test/MyTest.java").read_text()
        self.assertEqual(written, "class MyTest {}")

    def test_apply_patch_outside_scope_denied(self):
        session = self._make_session()
        result = tool_shell.tool_apply_patch(session, "src/main/MyService.java", "class Svc {}")
        self.assertFalse(result.ok)
        self.assertIn("Permission denied", result.error)

    def test_apply_patch_forbidden_path_denied(self):
        session = self._make_session()
        result = tool_shell.tool_apply_patch(session, ".ai/rules/architect.md", "HACKED")
        self.assertFalse(result.ok)
        self.assertIn("Permission denied", result.error)
        # Original file must be intact
        self.assertFalse((self.tmp.root / ".ai/rules/architect.md").exists())

    def test_complete_step_marks_done(self):
        session = self._make_session()
        result = tool_shell.tool_complete_step(session, "All tests written.")
        self.assertTrue(result.ok)
        self.assertTrue(session.completed)
        self.assertEqual(session.completion_summary, "All tests written.")

    def test_iteration_limit_enforced(self):
        session = self._make_session()
        # Exhaust iterations via reads
        for _ in range(tool_shell.MAX_ITERATIONS):
            tool_shell.tool_read_artifact(session, "nonexistent.md")
        # Next call should exceed limit
        result = tool_shell.tool_read_artifact(session, "x.md")
        self.assertFalse(result.ok)
        self.assertIn("Iteration limit", result.error)

    def test_dispatch_routes_correctly(self):
        self.tmp.write("docs/test.md", "hello")
        session = self._make_session()
        r = tool_shell.dispatch_tool(session, "read_artifact", {"path": "docs/test.md"})
        self.assertTrue(r.ok)

    def test_dispatch_unknown_tool(self):
        session = self._make_session()
        r = tool_shell.dispatch_tool(session, "magic_tool", {})
        self.assertFalse(r.ok)
        self.assertIn("Unknown tool", r.error)


# ===========================================================================
# Test Suite: gate_runner
# ===========================================================================

class TestGateRunner(unittest.TestCase):

    def setUp(self):
        self.tmp = TempRepo().__enter__()
        self.tmp.write("docs/PROJECT_ENV.md", _MINIMAL_PROJECT_ENV)

    def tearDown(self):
        self.tmp.__exit__(None, None, None)

    def test_parse_project_env_finds_commands(self):
        manifest = gate_runner.load_manifest(self.tmp.root)
        self.assertIn("gradlew", manifest.compile_check)
        self.assertIn("gradlew", manifest.unit_test)
        self.assertIn("gradlew", manifest.integration_test)

    def test_gate_none_always_passes(self):
        manifest = gate_runner.load_manifest(self.tmp.root)
        result = gate_runner.run_gate("NONE", manifest, self.tmp.root)
        self.assertTrue(result.passed)

    def test_gate_unknown_fails(self):
        manifest = gate_runner.load_manifest(self.tmp.root)
        result = gate_runner.run_gate("BOGUS_GATE", manifest, self.tmp.root)
        self.assertFalse(result.passed)

    def test_missing_project_env_raises(self):
        with self.assertRaises(FileNotFoundError):
            gate_runner.load_manifest(Path("/nonexistent"))

    def test_red_phase_output_classifier_ok_patterns(self):
        ok_outputs = [
            "UnsupportedOperationException: Not implemented",
            "AssertionError: expected: <1> but was: <0>",
            "org.junit.AssertionFailedError: expected true",
            "AssertionFailedError",
        ]
        for out in ok_outputs:
            has_ok = any(p.search(out) for p in gate_runner._RED_PHASE_OK_PATTERNS)
            self.assertTrue(has_ok, f"Expected OK pattern match for: {out!r}")

    def test_red_phase_output_classifier_bad_patterns(self):
        bad_outputs = [
            "COMPILATION ERROR: cannot find symbol",
            "NullPointerException at line 42",
            "NoClassDefFoundError",
        ]
        for out in bad_outputs:
            has_bad = any(p.search(out) for p in gate_runner._RED_PHASE_BAD_PATTERNS)
            self.assertTrue(has_bad, f"Expected BAD pattern match for: {out!r}")


# ===========================================================================
# Test Suite: git_engine
# ===========================================================================

class TestGitEngine(unittest.TestCase):

    def test_is_protected_main(self):
        self.assertTrue(git_engine.is_protected("main"))

    def test_is_protected_master(self):
        self.assertTrue(git_engine.is_protected("master"))

    def test_is_protected_develop(self):
        self.assertTrue(git_engine.is_protected("develop"))

    def test_is_protected_stage_branch(self):
        self.assertTrue(git_engine.is_protected("stage_2_improvements"))

    def test_is_protected_release(self):
        self.assertTrue(git_engine.is_protected("release-1.0"))

    def test_agent_branch_not_protected(self):
        self.assertFalse(git_engine.is_protected("agent-feature-user-service"))

    def test_feature_branch_not_protected(self):
        self.assertFalse(git_engine.is_protected("feature/my-feature"))

    def test_agent_branch_name_format(self):
        name = git_engine.agent_branch_name("user-service")
        self.assertEqual(name, "agent-feature-user-service")


# ===========================================================================
# Test Suite: trace_logger
# ===========================================================================

class TestTraceLogger(unittest.TestCase):

    def setUp(self):
        self.tmp = TempRepo().__enter__()

    def tearDown(self):
        self.tmp.__exit__(None, None, None)

    def _make_timer(self) -> trace_logger.StepTimer:
        import time
        t = trace_logger.StepTimer()
        t.start()
        time.sleep(0.01)
        t.stop()
        return t

    def test_record_creates_file(self):
        trace_file = self.tmp.root / "docs" / "features" / "x" / "EXECUTION_TRACE.json"
        timer = self._make_timer()
        entry = trace_logger.record(
            trace_file=trace_file,
            step_id="01",
            agent_role="architect",
            model_used="native-environment-model",
            provider="native",
            timer=timer,
            prompt_tokens=1000,
            completion_tokens=500,
            exit_status="SUCCESS",
        )
        self.assertTrue(trace_file.exists())
        data = json.loads(trace_file.read_text())
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["step_id"], "01")
        self.assertEqual(data[0]["cost_usd"], 0.0)

    def test_record_appends_entries(self):
        trace_file = self.tmp.root / "docs" / "features" / "x" / "EXECUTION_TRACE.json"
        for step in ("01", "02", "03"):
            trace_logger.record(
                trace_file=trace_file, step_id=step, agent_role="architect",
                model_used="native-environment-model", provider="native",
                timer=self._make_timer(), prompt_tokens=100, completion_tokens=50,
                exit_status="SUCCESS",
            )
        data = json.loads(trace_file.read_text())
        self.assertEqual(len(data), 3)

    def test_cost_native_is_zero(self):
        entry = trace_logger.TraceEntry(
            step_id="01", agent_role="architect",
            model_used="native-environment-model", provider="native",
            timestamp_start="", timestamp_end="",
            duration_sec=1.0,
            prompt_tokens=1000, completion_tokens=500, total_tokens=1500,
            cost_usd=trace_logger._calculate_cost("native", "native-environment-model", 1000, 500),
            exit_status="SUCCESS",
        )
        self.assertEqual(entry.cost_usd, 0.0)

    def test_cost_anthropic_sonnet(self):
        cost = trace_logger._calculate_cost("anthropic", "claude-3-7-sonnet-20250219", 1_000_000, 1_000_000)
        # $3/1M prompt + $15/1M completion = $18
        self.assertAlmostEqual(cost, 18.0, places=2)

    def test_cost_openai_gpt4o(self):
        cost = trace_logger._calculate_cost("openai", "gpt-4o-2024-08-06", 1_000_000, 1_000_000)
        # $2.5 + $10 = $12.5
        self.assertAlmostEqual(cost, 12.5, places=2)

    def test_cost_unknown_model_is_zero(self):
        cost = trace_logger._calculate_cost("openai", "gpt-99-ultra", 1_000_000, 1_000_000)
        self.assertEqual(cost, 0.0)

    def test_session_totals_empty(self):
        totals = trace_logger.session_totals(Path("/nonexistent/trace.json"))
        self.assertEqual(totals["steps"], 0)
        self.assertEqual(totals["total_cost_usd"], 0.0)

    def test_session_totals_aggregates(self):
        trace_file = self.tmp.root / "EXECUTION_TRACE.json"
        for step in ("01", "02"):
            trace_logger.record(
                trace_file=trace_file, step_id=step, agent_role="writer",
                model_used="native-environment-model", provider="native",
                timer=self._make_timer(), prompt_tokens=500, completion_tokens=200,
                exit_status="SUCCESS",
            )
        totals = trace_logger.session_totals(trace_file)
        self.assertEqual(totals["steps"], 2)
        self.assertEqual(totals["total_tokens"], 1400)  # (500+200)*2


# ===========================================================================
# Runner
# ===========================================================================

if __name__ == "__main__":
    unittest.main(verbosity=2)
