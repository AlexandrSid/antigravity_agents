# QA Plan: User Service (Core CRUD, Search & Demographic Graph)

## Overview
- **Feature Identifier**: `user-service`
- **Source Contract**: `docs/features/user-service/SPEC.md`
- **PRD (stack / [TARGET] only)**: `docs/PRD.md` §1.1 — Java 21, Gradle Kotlin DSL, Spring Boot 3.3.x, Flyway, H2 TCP, REST HTTP/JSON, 3-tier Controller → Service → Repository
- **Engineering Standards**: `.ai/guidelines/engineering-standards.md`
- **Author**: `qa-planner` (sole creator; owns all checklist wording)
- **Consumers**: `skeleton-writer` (Section 0 stubs) · `test-writer` (automate `[ ]` Test: items; read-only on this file) · `code-writer` (implement; mark `[x]` only — no text edits)
- **Out of scope**: all PRD `[BACKLOG]` items (runtime parental-age validation on POST/PUT, bulk family address move, recursive genealogy, hard delete, auth, PG/MySQL migration)

---

## 0. Domain Interfaces & Skeletons (Skeleton Phase)
- [ ] Implement: DTO `UserCreateRequest` with fields `firstName`, `lastName`, `birthDate`, `email`, `phoneNumber`, `address`, `fatherId`, `motherId`
- [ ] Implement: DTO `UserUpdateRequest` with the same replacement fields as create (required demographics + optional nullables)
- [ ] Implement: DTO `UserResponse` with `id`, `firstName`, `lastName`, `birthDate`, `email`, `phoneNumber`, `isDeleted`, `address`, `fatherId`, `motherId`, `childrenIds`
- [ ] Implement: DTO `AddressDto` with `id` (outbound), `country`, `city`, `street`, `building`, `apartment`, `postalCode`
- [ ] Implement: JPA entity types `User` and `Address` as compile-time persistence types (no business behavior)
- [ ] Implement: `UserService` interface with signatures for create, getById, update, softDelete, search (no business logic)
- [ ] Implement: `UserRepository` interface with save / find-active-by-id / search signatures required by SPEC §2.2
- [ ] Implement: `AddressRepository` interface with match-by (country, city, street, building, apartment) and save
- [ ] Implement: `UserController` stubs for `POST /api/v1/users`, `GET /api/v1/users/{id}`, `PUT /api/v1/users/{id}`, `DELETE /api/v1/users/{id}`, `GET /api/v1/users`; each method throws `UnsupportedOperationException("Not implemented")`
- [ ] Implement: `application.yml` and `application-docker.yml` placeholders so the skeleton compiles against the split-config rule

---

## 1. REST API & Interaction Contracts
- [ ] Test: `POST /api/v1/users` with valid required fields only returns `201 Created`, `Location: /api/v1/users/{id}`, and `UserResponse` (`isDeleted=false`, optional fields null/empty, `childrenIds=[]`)
- [ ] Test: `POST /api/v1/users` with full payload (phone, address, fatherId, motherId) returns `201` and echoes those fields on `UserResponse` without exposing JPA entities
- [ ] Test: `GET /api/v1/users/{id}` for an active user returns `200 OK` and `UserResponse` including address, `fatherId`, `motherId`, and `childrenIds` of direct **active** children only
- [ ] Test: `PUT /api/v1/users/{id}` with a complete valid replacement body returns `200 OK` and `UserResponse` reflecting the new state
- [ ] Test: `DELETE /api/v1/users/{id}` on an active user returns `204 No Content` and empty body
- [ ] Test: `GET /api/v1/users` with no filters returns `200 OK` and a Spring-page JSON shape (`content`, `pageable.pageNumber`, `pageable.pageSize`, `pageable.offset`, `totalElements`, `totalPages`, `last`)
- [ ] Implement: `UserController` maps all five SPEC operations; DTO-only I/O; no entity in the JSON body
- [ ] Implement: Controller → Service → Repository layering with no layer skipping

---

## 2. RFC 7807 Failure Protocol
- [ ] Test: missing/blank `firstName` or `lastName` on POST/PUT returns `400`, `Content-Type: application/problem+json`, fields `type`/`title`/`status`/`detail`/`instance`, `title=Validation Failed`, and `invalidParams[]` with `name` + `reason`
- [ ] Test: invalid `email` (not `@Email`) on POST/PUT returns `400` Problem Details with `invalidParams` entry for `email`
- [ ] Test: `birthDate` in the future or missing (`@Past` / `@NotNull`) on POST/PUT returns `400` Problem Details
- [ ] Test: `firstName`/`lastName` longer than 100, address `street` longer than 150, `building` longer than 50, `postalCode` longer than 20 return `400` with `invalidParams`
- [ ] Test: `phoneNumber` that fails the SPEC phone `@Pattern` returns `400` with `invalidParams` for `phoneNumber`
- [ ] Test: unparseable JSON or invalid `birthDate` type returns `400`, `title=Malformed JSON Payload`, `type` ending in `/malformed-json` (no requirement for `invalidParams`)
- [ ] Test: `PUT` with `fatherId` or `motherId` equal to the target user id returns `400`, `title=Invalid Parent Relationship`, `type` ending in `/invalid-parent-relation`
- [ ] Test: `GET` / `PUT` / `DELETE` of a non-existent id returns `404`, `title=User Not Found`, `type` ending in `/user-not-found`
- [ ] Test: `GET` / `PUT` / `DELETE` of a soft-deleted id returns the same `404 User Not Found` as a missing id
- [ ] Test: `POST`/`PUT` with `fatherId` or `motherId` that does not exist returns `404`, `title=Parent User Not Found`, `type` ending in `/parent-not-found`
- [ ] Test: `POST`/`PUT` with `fatherId` or `motherId` pointing at a soft-deleted user returns `404 Parent User Not Found`
- [ ] Test: `POST`/`PUT` with an email already used by another **active** user returns `409`, `title=Email Conflict`, `type` ending in `/duplicate-email`
- [ ] Test: `POST`/`PUT` with a phone already used by another **active** user returns `409`, `title=Phone Number Conflict`, `type` ending in `/duplicate-phone`
- [ ] Implement: `@ControllerAdvice` (or equivalent) mapping all SPEC §4 errors to RFC 7807 Problem Details

---

## 3. PUT Null & Address Identity
- [ ] Test: `PUT` with `phoneNumber: null` persists SQL `NULL` on `users.phone_number` and returns `phoneNumber: null`
- [ ] Test: `PUT` with `fatherId: null` and/or `motherId: null` persists SQL `NULL` on those FKs and returns them as null
- [ ] Test: `PUT` with `address: null` sets `users.address_id` to SQL `NULL`, returns `address: null`, and leaves the previously linked `addresses` row unchanged (same id and field values)
- [ ] Test: omitted optional fields on `PUT` are treated as `null` (full replacement, not merge) — previous phone/parents/address are cleared
- [ ] Test: `PUT` with an address object whose `apartment`/`postalCode` are `null` match-or-inserts a **new** address identity (country+city+street+building+apartment); does **not** UPDATE the old address row
- [ ] Test: two users sharing an address: updating one user's address (or unlinking it) does not change the other user's `address_id` or the shared row contents
- [ ] Test: `POST` with an address matching an existing row (country, city, street, building, apartment — `postalCode` not in match key) reuses that `address.id` and does not insert a duplicate
- [ ] Test: `POST` with a non-matching address inserts a new `addresses` row and links it
- [ ] Test: `PUT` of a soft-deleted target is `404` (not a silent update)
- [ ] Test: `PUT` that sets email/phone to a value used only by a soft-deleted user succeeds (`200`), not `409`
- [ ] Implement: PUT full-replacement, JSON-null → SQL `NULL`, address resolve/relink (match-or-insert, never UPDATE/DELETE address rows)

---

## 4. Soft Delete, Partial Indexes & Identity Reuse
- [ ] Test: `DELETE` of an active user sets `users.is_deleted = true` and does not issue a physical SQL `DELETE` of the `users` row
- [ ] Test: after `DELETE`, `GET /api/v1/users/{id}` returns `404 User Not Found`
- [ ] Test: after `DELETE`, `GET /api/v1/users` (search) does not include that user
- [ ] Test: `DELETE` of missing or already soft-deleted id returns `404 User Not Found`
- [ ] Test: `POST` with email/phone that exist only on `is_deleted = true` rows returns `201` (reuse allowed, not `409`)
- [ ] Test: `isDeleted` is not writable via create/update body (response remains server-managed; create always `false`)
- [ ] Test: `childrenIds` on GET excludes soft-deleted children
- [ ] Implement: soft-delete flag default `false`; active-only unique indexes `idx_users_email_active` and `idx_users_phone_active`; reuse path after delete
- [ ] Implement: hard unique indexes covering deleted rows are forbidden

---

## 5. Pagination, Search & Empty Results
- [ ] Test: `GET /api/v1/users` with no `page`/`size`/`sort` uses defaults `page=0`, `size=10`, `sort=id,asc` (`pageable.pageNumber=0`, `pageable.pageSize=10`)
- [ ] Test: `name` matches case-insensitive substring on `firstName` OR `lastName` (`LIKE %value%`) among active users only
- [ ] Test: `email` and `phone` filters are case-insensitive substring matches on those fields
- [ ] Test: `country`, `city`, `street`, `building`, `postalCode` filters match address fields as case-insensitive substrings
- [ ] Test: `familyMemberId` returns active users for whom that id is father, mother, or child
- [ ] Test: `familyMemberId` that is missing, soft-deleted, or has no active relatives returns `200` with `content: []` and `totalElements: 0`
- [ ] Test: filters that match nobody return `200` with `content: []` and `totalElements: 0` (not `404`)
- [ ] Test: search result set never includes `is_deleted = true` rows
- [ ] Implement: multi-criteria dynamic search and pagination as SPEC §2.1.5

---

## 6. Flyway Migrations & Seed Invariants
- [ ] Test / Verify: `V1__init_schema.sql` creates `addresses` and `users` with FKs (`address_id`, `father_id`, `mother_id`)
- [ ] Test / Verify: `V1__init_schema.sql` creates `idx_users_email_active` on `users(email) WHERE is_deleted = false`
- [ ] Test / Verify: `V1__init_schema.sql` creates `idx_users_phone_active` on `users(phone_number) WHERE is_deleted = false AND phone_number IS NOT NULL`
- [ ] Test / Verify: `V2__seed_1000_users.sql` loads exactly 20 addresses and 1,000 **active** users
- [ ] Test / Verify: seed inserts parents before children and `birthDate(parent) < birthDate(child)` in the seed dataset only
- [ ] Implement: Flyway `V1__init_schema.sql` and `V2__seed_1000_users.sql` as SPEC §2.2
- [ ] Implement: do **not** add runtime parental-age validation on POST/PUT (`[BACKLOG]`)

---

## 7. Assumption Traceability Matrix

| Field / Feature | Propagated Assumption | Target Test / Checklist Item | Attention for code-writer / test-writer |
|---|---|---|---|
| Stack / Lombok | `[AI-ASSUMPTION: Standard boilerplate reduction \| Attention Needed for Implementer]` | Section 0 DTOs / entities | Use Lombok only as boilerplate; do not change API contracts |
| `User.phoneNumber` pattern | `[AI-ASSUMPTION: Pattern ^\\+?[0-9. ()-]{7,25}$ \| Attention Needed for Implementer]` | §2 Test: phone `@Pattern` → `400` | Apply this regex on DTOs; do not invent another pattern |
| `User.children` / `childrenIds` | `[AI-ASSUMPTION: Children computed on demand to avoid recursion/cycle serialization \| Attention Needed for Implementer]` | §1 GET `childrenIds`; §4 excludes soft-deleted children | Compute on read; do not serialize a recursive `User` graph |
| Seed chronological order | `[AI-ASSUMPTION: Seed script chronological sanity \| Attention Needed for Implementer]` | §6 seed parent-before-child / birthDate order | Enforce in `V2` only; no runtime age check (backlog) |
