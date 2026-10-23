# Core Documentation & Reporting Meta-Standard

> **Layer**: `documentation`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: All active pipelines producing post-implementation reports, summaries, and API documentation.

---

## 1. Architectural Purpose
This meta-standard defines universal rules for generating technical summaries, API references, and verification evidence at the conclusion of development phases.
It ensures reports accurately reflect the physical repository state, prevents unintentional modifications to validated codebases, and provides human-readable verification proof.

---

## 2. Universal Documentation Invariants

### 2.1. Strict Read-Only Scope
1. During documentation phases, all source code (`src/**`), tests (`src/test/**`, `tests/`), build manifests, migrations, environment files, and specifications are strictly READ-ONLY.
2. The Documenter agent is forbidden from applying "minor cleanups" or modifying implementation code.

### 2.2. Evidence-Based Verification Reporting
1. All assertions of passed tests or working endpoints must reference confirmed execution results from pipeline Quality Gates.
2. Speculative or assumed status is prohibited.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

| Slot Identifier | Format | Description |
|---|---|---|
| `SUMMARY_STRUCTURE` | Markdown template | Layout for `docs/features/{feature}/SUMMARY.md`. |
| `EVIDENCE_CAPTURE_FORMAT` | Text | Required evidence blocks (unit test results, integration test results, curl verification). |

---

## 4. Agent Consumption Protocol
1. Read `SPEC.md`, `TDD_PLAN.md`, and execution logs.
2. Produce `docs/features/{feature}/SUMMARY.md` strictly following `slots/feature-summary.md`.
