# Core Environment & Manifest Meta-Standard

> **Layer**: `environment`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: All active pipelines interacting with local/remote toolchains, builds, tests, or preflight checks.

---

## 1. Architectural Purpose
This meta-standard defines the single source of truth for execution commands, toolchain prerequisites, preflight verification, and command security allowlisting.
It eliminates command guessing, ensures reproducible pipeline runs across diverse developer workstations, and protects environments against rogue executions.

---

## 2. Universal Environment Invariants

### 2.1. Single Source of Truth: `docs/PROJECT_ENV.md`
1. Every active feature or service must maintain an environment manifest titled `Local Project Environment & Commands` (discovered under `docs/PROJECT_ENV.md` or repository root).
2. All agents, runners, and orchestrators must dynamically bind execution commands strictly from this manifest.
3. Guessing or assuming commands (e.g. running unversioned `gradle`, `npm`, `mvn`, `cargo` without manifest binding) is STRICTLY FORBIDDEN.

### 2.2. Fail-Fast Pre-Flight Verification
1. Toolchain prerequisites (runtime version, environment variables, daemon status) must be verified before executing compilations or tests.
2. Agents must never blindly search host file systems for missing tools during code generation.
3. If a prerequisite is missing, the agent/orchestrator must halt immediately with clear actionable remediation steps.

### 2.3. Pre-Approved Command Allowlist
1. The Architect must exhaustively declare every terminal command needed across all development steps in the manifest's `Pre-Approved Command Allowlist`.
2. Execution hooks use this allowlist to permit trusted commands while halting on unapproved commands for user safety.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

Every environment slot under `slots/<tech>.md` MUST implement:

| Slot Identifier | Format | Description |
|---|---|---|
| `TOOLCHAIN_WRAPPER_CONVENTIONS` | Markdown list | Rules for project-local wrappers (`gradlew`, `mvnw`, virtual environments, local lockfiles). |
| `PREFLIGHT_CHECK_SPECIFICATION` | Code block | Standard check logic verifying runtime version, environment variables, and tool availability. |

---

## 4. Agent Consumption Protocol
1. **Discovery**: Discover the environment manifest titled `Local Project Environment & Commands` under `docs/` (fallback: repository root).
2. **Extraction**: Bind the exact commands from the manifest for build, test, and migration invocations.
3. **Safety**: Stop and escalate if the manifest or required command field is missing.
