# [Technology Name] Testing & TDD Slot Implementation

> **Layer**: `testing`  
> **Implements**: `.ai/guidelines/testing/META.md`

---

## 1. CANONICAL_STUB
```[language]
// Canonical Not-Implemented stub statement
```

## 2. FORBIDDEN_SILENT_STUBS
- `return null;`
- `return 0;`
- `return false;`
- `return "";`
- `return [];`
- `{}`

## 3. TEST_FRAMEWORK_AND_MOCK_LIB
- **Runner**: `[Framework Name and Version]`
- **Mock Library**: `[Mocking Tool Name]`

## 4. MOCK_PRECONFIGURATION_SYNTAX
```[language]
// Example preconfiguring mock response to prevent unconfigured default crashes
```

## 5. MOCK_BEHAVIOR_VERIFICATION_SYNTAX
```[language]
// Example asserting collaborator interaction
```

## 6. STUB_SCAN_PATTERNS
- `"[Pattern 1]"`
- `"[Pattern 2]"`

## 7. QUALITY_GATE_TASK_SNIPPET
```[config/tool]
// Runnable build tool task or linter target failing on stub presence
```
