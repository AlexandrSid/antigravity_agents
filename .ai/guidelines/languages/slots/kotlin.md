# Kotlin Tech Stack Slot Implementation

> **Layer**: `languages`  
> **Implements**: `.ai/guidelines/languages/META.md`  
> **Target**: Kotlin 2.x / JVM 21

---

## 1. TARGET_RUNTIME_VERSION
- **Target Runtime**: Strictly Java 21 LTS (`jvmTarget = "21"`).
- **Kotlin Version**: Kotlin 2.0+ (K2 compiler).

## 2. IMMUTABLE_DTO_SYNTAX
Use Kotlin `data class` with read-only (`val`) properties for all Request and Response DTOs:
```kotlin
data class CreateLinkRequest(
    val originalUrl: String,
    val customAlias: String? = null,
    val expiresAt: Instant? = null
)
```

## 3. NULLABILITY_SEMANTICS
1. DTOs and API contract models must explicitly differentiate between nullable fields (`String?`) and non-nullable required fields (`String`).
2. Serialization modules (Jackson Kotlin module or kotlinx.serialization) must be configured to reject missing required fields with a 400 Bad Request.

## 4. LANGUAGE_QUIRKS_AND_KEYWORDS
1. **Toolchain Integration**:
   - Explicit JVM 21 compatibility in build configuration:
     ```kotlin
     kotlin {
         jvmToolchain(21)
     }
     ```
2. **Data Class Copying**:
   - Prefer immutable transformations via `.copy(...)` over mutable property setters.
