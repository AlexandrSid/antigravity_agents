# Claude Code Agent Router
- Agent Roles: Read `.ai/rules/` (planner, architect, tdd-planner, environment-bootstrap, skeleton-writer, test-writer, code-writer, integration-test-writer, integration-code-writer).
- Pipeline Protocol: Read `.ai/GREENFIELD_SDLC_PIPELINE.md`.
- LLM Routing: Read `.ai/agents-config.md` and switch the IDE model to the bound profile before starting a role.
- Engineering Standards: Read `.ai/guidelines/engineering-standards.md`.
- Environment Manifest: auto-discover the file titled `Local Project Environment & Commands` under `docs/` (fallback: repository root). Use only its Compile / Build Check, Run All Tests, Run Single Test, and Database Migration fields.
- Always prefix responses with your active role tag (e.g. `**[architect.md]**:`).
