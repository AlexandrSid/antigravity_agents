# Testing Layer Extension Guide: Adding New Technology Slots

> **Layer**: `testing`  
> **Purpose**: Standardized procedure for onboarding a new language/testing-stack into `.ai/guidelines/testing/slots/`.

---

## 1. Quick Onboarding Process
To add support for a new technology (e.g. Go, Rust, C#, PHP):
1. Copy `slots/TEMPLATE.md` to `slots/<technology>.md` (all lowercase, e.g. `slots/rust.md`).
2. Fill in all 7 mandatory slots defined in `META.md`.
3. Verify that the quality gate snippet runs cleanly in the target build tool.
4. Add the slot reference to `SPEC.md` when designing a feature using this technology.

---

## 2. Definition of Done for a Testing Slot
A testing slot is complete and acceptable ONLY when:
- [ ] `CANONICAL_STUB` provides an uncatchable/explicit fatal or exception statement in the idiomatic style of the language.
- [ ] `FORBIDDEN_SILENT_STUBS` lists all false-positive returns (nulls, empty collections, default primitives, empty blocks).
- [ ] `MOCK_BEHAVIOR_VERIFICATION_SYNTAX` shows explicit interaction verification with parameter assertions.
- [ ] `QUALITY_GATE_TASK_SNIPPET` provides a runnable script or build task that fails with a non-zero exit code on stub presence.
