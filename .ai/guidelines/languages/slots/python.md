# Python Tech Stack Slot Implementation

> **Layer**: `languages`  
> **Implements**: `.ai/guidelines/languages/META.md`  
> **Target**: Python 3.11+

---

## 1. TARGET_RUNTIME_VERSION
- **Python Version**: Python 3.11+.

## 2. IMMUTABLE_DTO_SYNTAX
Use Pydantic v2 `BaseModel` with `frozen=True` or standard library `@dataclass(frozen=True)`:
```python
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime
from typing import Optional

class CreateLinkRequest(BaseModel):
    model_config = ConfigDict(frozen=True)

    original_url: str = Field(..., max_length=2048)
    custom_alias: Optional[str] = Field(None, max_length=255)
    expires_at: Optional[datetime] = None
```

## 3. NULLABILITY_SEMANTICS
1. Use `Optional[T]` or native Python 3.10+ union `T | None` for optional fields.
2. Mandatory fields must not have default values and must be validated on ingress. Missing mandatory fields trigger Pydantic `ValidationError` mapped to RFC 7807 400 Bad Request.

## 4. LANGUAGE_QUIRKS_AND_KEYWORDS
1. **Type Annotations**:
   - All function signatures must include return type hints (`-> ReturnType:`).
2. **Environment & Packaging**:
   - Project-local virtual environment (`.venv`) or modern packaging tool (`uv` / `poetry`).
