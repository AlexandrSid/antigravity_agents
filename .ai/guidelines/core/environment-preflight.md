# Core Environment Manifest & Pre-Flight Standards

> **Scope**: Universal & Platform-Agnostic  
> **Mandatory**: Defines the project execution contract between the Architect, the environment, and the orchestrator.

---

## 1. Single Source of Truth: `docs/PROJECT_ENV.md`
Every project feature must maintain a verified environment manifest at `docs/PROJECT_ENV.md`. Downstream agents, Quality Gate runners, and CI/CD pipelines auto-discover this manifest.

### Mandatory Manifest Structure:
```markdown
# Local Project Environment & Commands

## Service Metadata
- **Service Identifier**: {service-name}
- **Tech Stack**: {Exact language, runtime versions, build tool, framework, database}

## Toolchain Prerequisites
- **Runtime**: {e.g. JDK 21 / Python 3.11 / Rust 1.80 / Node 20}
- **Environment Variables**: {e.g. JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"}
- **Daemon Requirements**: {e.g. Docker Daemon running}

## Preflight Verification Command
- **Preflight Check**: `python python-runtime/preflight_check.py`

## Execution Commands
- **Compile / Build Check**: {Exact command, e.g. .\gradlew.bat compileKotlin}
- **Unit / Slice Test**: {Exact command, e.g. .\gradlew.bat test}
- **Integration / E2E Test**: {Exact command, e.g. .\gradlew.bat test --tests "*IntegrationTest"}
- **Run Single Test**: {Exact command, e.g. .\gradlew.bat test --tests "{test_class}"}
- **Database Migration**: {Exact command, e.g. .\gradlew.bat flywayMigrate}

## Pre-Approved Command Allowlist
- `{command 1}`
- `{command 2}`
- `{command 3}`
```

---

## 2. Fail-Fast Pre-Flight Contract (Step 00)
1. **Pre-flight Gate**:
   - Before executing code generation (Step 04) or running any tests, the orchestrator or developer runs `preflight_check.py`.
2. **Prohibition of Blind Discovery**:
   - Agents are strictly forbidden from scanning the file system (`Get-ChildItem`, `dir /s`, `find`) in search of missing tools or incompatible JDKs during code generation.
3. **Fail-Fast Halt**:
   - If a prerequisite (e.g. JDK 21, Docker Daemon, Build Wrapper) is missing or mismatched, the pipeline **must immediately abort** with a structured instruction:
     ```text
     [PRE-FLIGHT FAILED]: Toolchain prerequisite missing:
       - Required: JDK 21
       - Action: set $env:JAVA_HOME = "..." and restart pipeline.
     ```

---

## 3. Pre-Approved Command Allowlist Contract
1. **Exhaustive Declaration**:
   - The Architect (Step 01) must exhaustively declare every terminal command needed across all steps in the `Pre-Approved Command Allowlist`.
2. **Auto-Approval Integration**:
   - Lifecycle hooks (e.g., `PreToolUse` in Antigravity) use this list to auto-allow trusted commands without requiring human-in-the-loop manual confirmation dialogues for every single build or test run.
   - Any command not matching the allowlist halts with a permission review prompt to ensure safety against rogue executions.
