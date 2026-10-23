# Java Tech Stack Slot Implementation

> **Layer**: `languages`  
> **Implements**: `.ai/guidelines/languages/META.md`  
> **Target**: Java 21 LTS

---

## 1. TARGET_RUNTIME_VERSION
- **Java Version**: Java 21 LTS.
- **Compiler Compatibility**: Source Compatibility = 21, Target Compatibility = 21.
- **Compiler Flags**: `-parameters` (preserves method parameter names for reflection and framework bindings).

## 2. IMMUTABLE_DTO_SYNTAX
Use Java `record` for all Request and Response DTOs and Value Objects:
```java
public record CreateLinkRequest(
    @NotBlank(message = "Original URL cannot be blank")
    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    String originalUrl,

    @Size(max = 255, message = "Custom alias cannot exceed 255 characters")
    String customAlias,

    Instant expiresAt
) {}
```

## 3. NULLABILITY_SEMANTICS
- Java reference types are nullable by default.
- Use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@NotEmpty`) to mandate presence.
- For optional fields, allow raw `null` on the record component; unpack safely using `Optional.ofNullable(...)` in service logic.
- Do not use `Optional<T>` as a record field type or constructor parameter.

## 4. LANGUAGE_QUIRKS_AND_KEYWORDS
1. **Underscore Keyword Restriction**:
   - In Java 21, the single underscore `_` is a strictly reserved keyword and cannot be used as an identifier, variable, parameter name, or dynamically generated alias.
2. **Modern Idioms**:
   - Use Pattern Matching for `switch` and `instanceof`.
   - Prefer text blocks `"""..."""` for multiline SQL or JSON strings.
