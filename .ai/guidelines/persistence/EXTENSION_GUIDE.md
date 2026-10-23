# Persistence Layer Extension Guide: Adding New Database Slots

> **Layer**: `persistence`  
> **Purpose**: Standardized procedure for onboarding a new database or migration engine into `.ai/guidelines/persistence/slots/`.

---

## 1. Quick Onboarding Process
1. Copy `slots/TEMPLATE.md` to `slots/<database>.md` (all lowercase, e.g. `slots/mysql.md`).
2. Fill in all 4 mandatory slots defined in `META.md`.
3. Check whether the target engine supports predicate indexes (`WHERE ...`); if not, document the service-layer fallback requirement.
4. Add the slot reference to `SPEC.md` when designing a feature using this database.

---

## 2. Definition of Done for a Persistence Slot
- [ ] Migration tool and folder location explicitly pinned.
- [ ] Partial/predicate index support clearly documented with syntax or lack of support note.
- [ ] Working SQL sequence reset statement provided.
- [ ] Functional container healthcheck definition provided.
