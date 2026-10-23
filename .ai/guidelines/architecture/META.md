# Core Architecture & API Standards Meta-Standard

> **Layer**: `architecture`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: All active pipelines developing or modifying service architectures and HTTP/API boundaries.

---

## 1. Architectural Purpose
This meta-standard defines universal design principles, boundary isolation rules, error schemas, and state mutation semantics across all platforms.
It guarantees clean separation of concerns, zero entity leakage, predictable REST semantics, and unified error observability.

---

## 2. Universal Architecture Invariants

### 2.1. Strict 3-Tier Layering & Boundaries
1. **Layer Sequence**: `Interface / Controller / Route Handler` → `Domain / Service` → `Repository / Persistence`.
2. **Downward Dependency Direction**: Interfaces depend only on domain contracts; services depend on domain rules and repository contracts; repositories depend on data stores. Upward calls are forbidden.
3. **Entity Exposure Barrier**:
   - Internal persistence models (JPA/ORM entities, document models, raw DB records) **must never** be exposed across external API responses or public contracts.
   - Strictly use DTOs (Data Transfer Objects), View Models, or Value Objects at the boundary.
4. **Environment Isolation**:
   - Runtime configuration must be modularized by profile/environment (e.g., local, docker, test, prod). Hardcoding environment-specific URLs or secrets in code is forbidden.

### 2.2. Universal Error Contract (RFC 7807 Problem Details)
All error responses across HTTP and synchronous API boundaries must conform to **RFC 7807 Problem Details** (`Content-Type: application/problem+json`):
1. **Mandatory Fields**:
   - `type` (URI identifier for the error type)
   - `title` (short, human-readable summary)
   - `status` (HTTP status code matching the error category)
   - `detail` (detailed explanation of this specific occurrence)
   - `instance` (URI or resource path of the failed request)
2. **Validation Failures**:
   - Must include an `invalidParams` array specifying field-level `{ "name": "...", "reason": "..." }`.
3. **Standard Status Code Semantics**:
   - `400 Bad Request`: Payload validation failures, malformed input syntax, illegal domain self-relations.
   - `404 Not Found`: Target resource missing or soft-deleted; referenced foreign entity missing or soft-deleted.
   - `409 Conflict`: Uniqueness collisions among active entities, concurrent modification conflicts, domain invariant collisions.

### 2.3. Soft Delete Invariants
1. **Physical Deletion Prohibition**:
   - Business entities with audit requirements are never physically deleted. `DELETE` operations mark `is_deleted = true` (or timestamp `deleted_at`).
2. **Query Exclusion**:
   - Standard read, search, and list operations must unconditionally exclude soft-deleted entities (`is_deleted = false`).
3. **Active Uniqueness Invariant**:
   - Domain uniqueness (e.g. code, email, alias) is enforced **only among active records** (`is_deleted = false`).
   - Soft-deleted identities may be reclaimed and reused by new active records.
   - *Database Support*: If the target database does not support partial/conditional unique indexes (e.g. H2, MySQL < 8.0.13), uniqueness checks **must** be enforced at the transactional service/domain logic layer.

### 2.4. Full Replacement (PUT) Null-Handling Contract
1. **PUT Semantics**:
   - `PUT` is full-resource replacement, not a partial patch.
   - Explicit JSON `null` on optional fields **explicitly resets** those persistent attributes to SQL `NULL` / absent.
2. **Shared Sub-resource Safety**:
   - Updates to linked sub-entities must be isolated: relink to an existing matching record or insert a new one. Never mutate shared records utilized by other entities.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

Every framework slot under `slots/<tech>.md` MUST implement the following slots:

| Slot Identifier | Format | Description |
|---|---|---|
| `ROUTING_AND_CONTROLLER_STRUCTURE` | Code block | Idiomatic route registration, resource annotations, and path mappings. |
| `DTO_VALIDATION_MAPPING` | Code block | Ingress validation setup and annotation bindings. |
| `RFC7807_ERROR_MAPPER_IMPLEMENTATION` | Code block | Global exception mapper producing `application/problem+json`. |
| `DI_ANNOTATIONS_AND_SCOPE` | Markdown list | Dependency injection scoping, bean lifecycle, and repository boundaries. |

---

## 4. Agent Consumption Protocol
1. **Discovery**: Read `SPEC.md` (`## Applicable Technical Standards`) to identify the framework slot.
2. **Extraction**: Open `.ai/guidelines/architecture/slots/<tech>.md` and apply layering, validation, and RFC 7807 mapper standards.
