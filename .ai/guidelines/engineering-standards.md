# Engineering Standards

Project-wide technical constraints. All agents in `.ai/rules/` must treat these as hard rules. Feature-specific contracts live in `docs/`; do not duplicate them here.

## 1. Architecture
- Strict 3-tier layering: Controller → Service → Repository.
- JPA entities are never exposed in API responses. DTOs only at the Controller boundary.
- Configuration is split by environment (`application.yml`, `application-docker.yml`).
- Derive stack and protocols from `docs/PRD.md` Section 1.1. Do not introduce unapproved technologies.

## 2. RFC 7807 Error Contract
- All error responses use **RFC 7807 Problem Details** (`Content-Type: application/problem+json`).
- Required fields: `type`, `title`, `status`, `detail`, `instance`.
- Validation failures add `invalidParams` (field-level `name` + `reason`).
- Status mapping:
  - `400` — schema/validation failure, malformed payload, illegal self-parent relation
  - `404` — target missing or soft-deleted; referenced parent missing or soft-deleted
  - `409` — uniqueness collision on active (`is_deleted = false`) email or phone

## 3. Soft Delete
- Resources are never physically deleted. `DELETE` sets `is_deleted = true`.
- Standard reads and searches exclude soft-deleted rows (`WHERE is_deleted = false`).
- Uniqueness is enforced only among active rows (partial unique indexes on email/phone).
- Soft-deleted identities may be reused by new active records.

## 4. PUT Null Handling
- `PUT` is full-resource replacement, not a partial patch.
- JSON `null` on optional fields **explicitly resets** those columns to SQL `NULL` (e.g. phone, parents, address, address apartment/postal code).
- Address updates are individual: relink to an existing matching address or insert a new row; do not mutate address rows shared by other records.
