# Environment Layer Extension Guide: Adding New Environment Slots

> **Layer**: `environment`  
> **Purpose**: Standardized procedure for onboarding new operating environments or toolchains into `.ai/guidelines/environment/slots/`.

---

## 1. Quick Onboarding Process
1. Create `slots/<profile>.md` (e.g. `slots/local-dev.md`, `slots/ci-github-actions.md`).
2. Document toolchain wrapper requirements and preflight check definitions.
3. Wire into the project manifest generation.
