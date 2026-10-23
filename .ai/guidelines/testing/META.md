# Universal Testing & TDD Lifecycle Meta-Standard

> **Layer**: `testing`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: All active pipelines with testing, verification, and code generation phases.

---

## 1. Architectural Purpose
This meta-standard defines the universal contracts, state transitions, and quality gates for automated testing across the SDLC.
It eliminates testing deadlocks, prevents "silent passes" over unimplemented code, and ensures tests verify real collaborator behavior rather than mock accidents or hardcoded return values.

---

## 2. Universal Testing Invariants

### 2.1. Phase 0: Type Surface & Skeleton Compilation Stubs
1. **Compilation Precondition**: Source code must compile cleanly with all types, DTOs, and signatures before any test is authored.
2. **Canonical Not-Implemented Signal**: Every method or procedure body in the skeleton MUST immediately raise the canonical Not-Implemented signal defined by the technology slot.
3. **Strict Prohibition of Silent Stubs**:
   - Returning `null`, `undefined`, `nil`, or absence indicators is STRICTLY FORBIDDEN.
   - Returning `0`, `false`, empty strings `""`, or empty collections/maps/containers is STRICTLY FORBIDDEN.
   - Using `TODO`/`FIXME` comments combined with fallback values is STRICTLY FORBIDDEN.
   - Empty procedure bodies (implicit void returns without raising the Not-Implemented signal) are STRICTLY FORBIDDEN.
   - *Rationale*: Silent stubs fail silently to trigger the Red Phase, causing test-writer to write incomplete or deadlocked tests that downstream code-writers cannot fix.

### 2.2. Phase 1: Unit & Slice Tests (Red Phase)
1. **Dependency Isolation**: Unit and slice tests must run in-memory with isolated dependencies (mocks, stubs, test doubles).
2. **Mandatory Mock Pre-Configuration**:
   - The test author MUST configure explicit, valid return values for all collaborator invocations expected in the scenario.
   - A test must NEVER fail due to unconfigured test double defaults (e.g., null pointer dereferences, undefined attribute lookups).
   - *Red Phase Gate Failure Rule*: If any test fails with a null dereference or harness setup error instead of the canonical Not-Implemented signal, the Red Phase Gate FAILS.
3. **Mandatory Behaviour Verification (Interaction Assertions)**:
   - When the component under test interacts with an external collaborator (service, repository, client, cache, gateway), the test MUST include an explicit assertion verifying that the collaborator was called with expected arguments.
   - Asserting only returned state without verifying collaborator interaction is INCOMPLETE and rejected.

### 2.3. Phase 2: Green-Phase Implementation & Stub Eradication
1. **Minimal Production Code**: Implement only the minimal logic required to pass the existing unit tests without modifying tests.
2. **Complete Stub Eradication**:
   - Primary production source directories must contain ZERO matches for any placeholder stub patterns.
3. **Machine-Verifiable Quality Gate**:
   - The project build system must contain an automated task/check that scans production sources for stub patterns and exits non-zero if any remain.
   - The Green Phase is incomplete until this gate passes with exit code 0.

### 2.4. Integration & E2E Verification
1. **Strict Partitioning**: Integration tests requiring databases, containers, or network calls must be partitioned from unit tests.
2. **Clean State Isolation**: Each integration test run must execute against a clean, isolated state / volume.
3. **Zero Architectural Drift**: Integration test fixes must not alter DDL or schemas without explicit architecture review.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

Every technology slot under `slots/<tech>.md` MUST implement the following slots:

| Slot Identifier | Format | Description |
|---|---|---|
| `CANONICAL_STUB` | Code block | Exact statement throwing/raising the Not-Implemented signal. |
| `FORBIDDEN_SILENT_STUBS` | List of strings | Exact patterns that are prohibited in skeleton stubs. |
| `TEST_FRAMEWORK_AND_MOCK_LIB` | Markdown description | Name and canonical packages for runner and test doubles. |
| `MOCK_PRECONFIGURATION_SYNTAX` | Code block | Syntax for configuring stubbed responses on mocks. |
| `MOCK_BEHAVIOR_VERIFICATION_SYNTAX` | Code block | Syntax for verifying collaborator interaction (`verify`). |
| `STUB_SCAN_PATTERNS` | List of strings | Exact search patterns for the anti-stub scan. |
| `QUALITY_GATE_TASK_SNIPPET` | Code block | Concrete build tool task or script definition. |

---

## 4. Agent Consumption Protocol
1. **Discovery**: Read `SPEC.md` (`## Applicable Technical Standards`) to identify the project's testing technology slot.
2. **Extraction**: Open `.ai/guidelines/testing/slots/<tech>.md` and load the slot definitions into working context.
3. **Strict Validation**: If any mandatory slot is missing from `<tech>.md`, the agent MUST halt execution and escalate. Do not guess or invent syntax.
