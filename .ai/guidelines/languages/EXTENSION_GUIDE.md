# Languages Layer Extension Guide: Adding New Language Slots

> **Layer**: `languages`  
> **Purpose**: Standardized procedure for onboarding a new programming language into `.ai/guidelines/languages/slots/`.

---

## 1. Quick Onboarding Process
1. Copy `slots/TEMPLATE.md` to `slots/<language>.md` (all lowercase, e.g. `slots/go.md`).
2. Fill in all 4 mandatory slots defined in `META.md`.
3. Ensure the immutable DTO example shows both required and optional fields.
4. Add the slot reference to `SPEC.md` when designing a feature using this language.

---

## 2. Definition of Done for a Language Slot
- [ ] Explicit pinned runtime version declared.
- [ ] Immutable DTO syntax idiomatic to the language.
- [ ] Explicit nullability semantics and missing-field failure rules documented.
- [ ] Any language-specific keywords, underscore restrictions, or compiler flags highlighted.
