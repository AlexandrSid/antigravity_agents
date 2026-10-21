# Infrastructure Layer Extension Guide: Adding New Infrastructure Slots

> **Layer**: `infrastructure`  
> **Purpose**: Standardized procedure for onboarding a new orchestration or container tool into `.ai/guidelines/infrastructure/slots/`.

---

## 1. Quick Onboarding Process
1. Copy `slots/TEMPLATE.md` to `slots/<tool>.md` (all lowercase, e.g. `slots/kubernetes.md`).
2. Fill in all 3 mandatory slots defined in `META.md`.
3. Verify that non-root user and healthcheck condition requirements are enforced.
4. Add the slot reference to `SPEC.md` when designing a feature using this infrastructure.

---

## 2. Definition of Done for an Infrastructure Slot
- [ ] Base images specify non-root user and pinned versions.
- [ ] Ephemeral volume and project namespace parameters clearly exposed.
- [ ] Healthcheck dependency blocks documented.
