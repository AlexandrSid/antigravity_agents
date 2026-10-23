# Architecture Layer Extension Guide: Adding New Framework Slots

> **Layer**: `architecture`  
> **Purpose**: Standardized procedure for onboarding a new web/API framework into `.ai/guidelines/architecture/slots/`.

---

## 1. Quick Onboarding Process
1. Copy `slots/TEMPLATE.md` to `slots/<framework>.md` (all lowercase, e.g. `slots/fastapi.md`).
2. Fill in all 4 mandatory slots defined in `META.md`.
3. Ensure the RFC 7807 error mapper explicitly handles validation failures with `invalidParams`.
4. Add the slot reference to `SPEC.md` when designing a feature using this framework.

---

## 2. Definition of Done for an Architecture Slot
- [ ] Idiomatic controller / router structure demonstrated with path parameters and query arguments.
- [ ] Ingress request validation demonstrated.
- [ ] Global exception mapper returning `Content-Type: application/problem+json` matching RFC 7807.
- [ ] Dependency injection scopes and repository access conventions clarified.
