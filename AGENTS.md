# Universal Agent Specifications
This repository uses centralized AI agent definitions:
- Core Roles: `.ai/rules/`
- Greenfield Pipeline: `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`
- Pipeline State Standard & Resume Protocol: `.ai/guidelines/core/pipeline-state-standards.md`
- LLM Routing: `.ai/agents-config.md`
- Project Guidelines: `.ai/guidelines/core/` (universal) & `.ai/guidelines/stacks/` (modular)
- Environment Orchestrators:
  - Antigravity IDE: `.antigravity/orchestrator.md` & `.agents/skills/sdlc-orchestrator/`
  - Cursor: `.cursor/rules/orchestrator.md`
  - Windsurf: `.windsurfrules`
  - Claude Code: `CLAUDE.md`
- Standalone Runtime Toolchain: `python-runtime/` (Gate Runner, Git Engine, Pipeline Parser, Standalone Runner)
- Environment Manifest: auto-discover the file titled `Local Project Environment & Commands` under `docs/` (fallback: repository root). Do not hardcode toolchain commands.
