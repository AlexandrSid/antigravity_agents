# Core Architecture Standards

> **Scope**: Universal & Platform-Agnostic  
> **Mandatory**: All agent roles across all pipelines must adhere strictly to these rules.

---

## 1. Strict Layering & Boundaries
1. **Strict 3-Tier Layering**: `Controller / Interface` → `Service / Domain` → `Repository / Persistence`.
2. **Entity Exposure Barrier**:
   - Internal persistence models (JPA/ORM entities, document models, raw DB records) **must never** be exposed in public API responses or network contracts.
   - Strictly use DTOs (Data Transfer Objects), View Models, or Value Objects at the boundary.
3. **Environment Isolation**:
   - Runtime configuration must be modularized by profile/environment (e.g., local, docker, test, prod). Hardcoding environment-specific URLs or secrets in code is forbidden.

---

## 2. Universal Error Contract (RFC 7807)
All error responses across HTTP and synchronous API boundaries must conform to **RFC 7807 Problem Details** (`Content-Type: application/problem+json`):

1. **Mandatory Fields**:
   - `type` (URI identifier for the error type)
   - `title` (short, human-readable summary)
   - `status` (HTTP/transport status code matching the error category)
   - `detail` (detailed human-readable explanation of this specific occurrence)
   - `instance` (URI or resource path of the failed request)
2. **Validation Failures**:
   - Must include an `invalidParams` array specifying field-level `{ "name": "...", "reason": "..." }`.
3. **Standard Status Code Semantics**:
   - `400 Bad Request`: Payload validation failures, malformed input syntax, illegal domain self-relations.
   - `404 Not Found`: Target resource missing, or soft-deleted; referenced foreign entity missing or soft-deleted.
   - `409 Conflict`: Uniqueness collisions among active entities, concurrent modification conflicts, domain invariant collisions.

---

## 3. Soft Delete Invariants
1. **Physical Deletion Prohibition**:
   - Business entities with audit requirements are never physically deleted. `DELETE` operations mark `is_deleted = true` (or equivalent timestamp `deleted_at`).
2. **Query Exclusion**:
   - Standard read, search, and list operations must unconditionally exclude soft-deleted entities (`is_deleted = false`).
3. **Active Uniqueness Invariant**:
   - Domain uniqueness (e.g., unique email, unique phone number, unique code) is enforced **only among active records** (`is_deleted = false`).
   - Soft-deleted identities may be reclaimed and reused by new active records.
   - *Database Support*: If the target database does not support predicate/partial unique indexes (e.g. H2, MySQL), uniqueness checks **must** be enforced at the transactional service/domain logic layer.

---

## 4. Full Replacement (PUT) Null-Handling Contract
1. **PUT Semantics**:
   - `PUT` is full-resource replacement, not a partial patch.
   - Explicit JSON `null` on optional fields **explicitly resets** those persistent attributes to SQL `NULL` / absent.
2. **Shared Sub-resource Safety**:
   - Updates to linked sub-entities (e.g. addresses) must be isolated: relink to an existing matching record or insert a new one. Never mutate shared records utilized by other entities.
