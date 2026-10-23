# Universal Language & Runtime Meta-Standard

> **Layer**: `languages`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: All active pipelines generating or modifying application code.

---

## 1. Architectural Purpose
This meta-standard defines the universal constraints on type safety, data modeling, nullability, and language idioms across the SDLC.
It guarantees that source code is strictly typed, data models at boundaries are immutable, and runtime language quirks do not compromise build stability.

---

## 2. Universal Language Invariants

### 2.1. Strict Typing
1. All method/function signatures, API boundaries, DTOs, and domain models must have explicit type declarations.
2. Untyped placeholders (`any`, `Object`, untyped dictionaries) are strictly prohibited at architectural boundaries.

### 2.2. Immutable Data Carriers (DTOs)
1. Inbound Request DTOs and outbound Response DTOs must be modeled as **immutable data structures** (e.g. Java `record`, Kotlin `data class`, Python `@dataclass(frozen=True)` or Pydantic `frozen=True`, TypeScript readonly types).
2. DTOs must have zero domain logic; they are pure carriers of validated state.

### 2.3. Explicit Nullability & Boundary Validation
1. DTOs and API contracts must explicitly distinguish between optional/nullable fields and mandatory required fields.
2. Framework-level serialization must be configured to fail fast (400 Bad Request) on missing required fields.

### 2.4. Keyword & Version Constraints
1. Every project must explicitly target a LTS or pinned runtime version.
2. Reserved keywords of newer language editions (e.g. single underscore `_` in Java 21) must never be used as identifiers.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

Every language slot under `slots/<tech>.md` MUST implement the following slots:

| Slot Identifier | Format | Description |
|---|---|---|
| `TARGET_RUNTIME_VERSION` | Text | Exact version and LTS status of the runtime. |
| `IMMUTABLE_DTO_SYNTAX` | Code block | Idiomatic immutable DTO definition for request/response models. |
| `NULLABILITY_SEMANTICS` | Markdown list | Rules for null-safety, optional types, and deserialization rejection. |
| `LANGUAGE_QUIRKS_AND_KEYWORDS` | Markdown list | Reserved keywords, compiler flags, and toolchain constraints. |

---

## 4. Agent Consumption Protocol
1. **Discovery**: Read `SPEC.md` (`## Applicable Technical Standards`) to identify the project's language slot.
2. **Extraction**: Open `.ai/guidelines/languages/slots/<tech>.md` and apply its constraints during skeleton, test, and code writing.
3. **Strict Validation**: If a required language slot is missing, halt and escalate.
