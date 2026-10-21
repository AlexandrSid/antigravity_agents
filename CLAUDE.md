# Claude Code Agent Router & Orchestrator
- Agent Roles: Read `.ai/rules/` (planner, architect, tdd-planner, environment-bootstrap, skeleton-writer, test-writer, code-writer, integration-test-writer, integration-code-writer).
- Pipeline Protocol: Read `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`.
- Pipeline State & Resume Protocol: Always check `docs/features/{feature}/PIPELINE_STATE.md` before starting, resume from the first pending step, and update checkboxes upon gate pass per `.ai/guidelines/pipeline-state-standards.md`.
- Quality Gates: Execute verification commands using `python python-runtime/gate_runner.py . <GATE>`.
- LLM Routing & External Models: Read `.ai/agents-config.md`. For external/local models in Claude Code, configure `ANTHROPIC_BASE_URL` with a LiteLLM proxy pointing to Ollama or custom providers.
- Engineering Standards: Read layer meta-standards and technology slots under `.ai/guidelines/<layer>/`.
- Environment Manifest: auto-discover `docs/PROJECT_ENV.md`.
- Always prefix responses with your active role tag (e.g. `**[architect.md]**:` or `**[orchestrator.claude]**:`).
