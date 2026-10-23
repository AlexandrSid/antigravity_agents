# Feature Summary Slot Implementation

> **Layer**: `documentation`  
> **Implements**: `.ai/guidelines/documentation/META.md`  
> **Target**: Standard Feature Summary (`docs/features/{feature}/SUMMARY.md`)

---

## 1. SUMMARY_STRUCTURE
```markdown
# Feature Summary: {Feature Name}

## 1. Overview
- **Feature Identifier**: {feature}
- **Primary Contract**: `docs/features/{feature}/SPEC.md`
- **Execution Plan**: `docs/features/{feature}/TDD_PLAN.md`
- **Status**: Completed / Ready for Review

## 2. Implemented Capabilities & Endpoints
- `{METHOD} {path}` — {Description, payload, response code}

## 3. Engineering Invariants Verification
- Architecture Layering: Validated
- RFC 7807 Error Handling: Validated
- Soft Delete & Active Uniqueness: Validated
- Stub Eradication Gate: 0 matches

## 4. Verification Evidence
- Unit / Slice Tests: {N} passed, 0 failed
- Integration Tests: {M} passed, 0 failed
- Container Health: Verified
```
