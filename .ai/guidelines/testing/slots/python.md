# Python Testing & TDD Slot Implementation

> **Layer**: `testing`  
> **Implements**: `.ai/guidelines/testing/META.md`  
> **Target**: Python 3.11+ / pytest / unittest.mock

---

## 1. CANONICAL_STUB
```python
raise NotImplementedError("Not implemented: ClassName.method_name")
```

## 2. FORBIDDEN_SILENT_STUBS
- `return None`
- `return 0`
- `return False`
- `return ""`
- `return []`
- `return {}`
- `return set()`
- `pass`
- `...` (Ellipsis without raising exception)

## 3. TEST_FRAMEWORK_AND_MOCK_LIB
- **Runner**: `pytest`
- **Mock Library**: `unittest.mock` / `pytest-mock`

## 4. MOCK_PRECONFIGURATION_SYNTAX
```python
mock_collaborator.execute.return_value = valid_result
```

## 5. MOCK_BEHAVIOR_VERIFICATION_SYNTAX
```python
mock_collaborator.execute.assert_called_once_with(*expected_args, **expected_kwargs)
```

## 6. STUB_SCAN_PATTERNS
- `raise NotImplementedError`
- `NotImplementedError("Not implemented")`

## 7. QUALITY_GATE_TASK_SNIPPET
```makefile
# In Makefile or pyproject.toml task
check-no-stubs:
	@python -c 'import sys, re, glob; violations = [f"{fn}:{i+1}" for fn in glob.glob("src/**/*.py", recursive=True) for i, l in enumerate(open(fn)) if "NotImplementedError" in l]; (print("checkNoStubs FAILED:\n" + "\n".join(violations)) or sys.exit(1)) if violations else print("checkNoStubs passed.")'
```
