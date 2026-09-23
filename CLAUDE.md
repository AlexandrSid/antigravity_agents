# Claude Code Agent Router
- Agent Roles: Read `.ai/rules/` (planner, architect, qa-planner, skeleton-writer, test-writer, code-writer).
- Engineering Standards: Read `.ai/guidelines/engineering-standards.md`.
- Environment Manifest: auto-discover the file titled `Local Project Environment & Commands` under `docs/` (fallback: repository root). Use only its Compile / Build Check, Run All Tests, Run Single Test, and Database Migration fields.
- Always prefix responses with your active role tag (e.g. `**[architect.md]**:`).
