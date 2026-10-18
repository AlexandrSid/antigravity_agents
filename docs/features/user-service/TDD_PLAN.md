# TDD Plan: user-service

## Overview
- **Feature Identifier**: `user-service`
- **Source Contract**: `docs/features/user-service/SPEC.md`
- **Engineering Standards**: `.ai/guidelines/engineering-standards.md`
- **Author**: `tdd-planner` (sole creator; owns all task and test definitions)
- **Consumers**: `skeleton-writer` (Section 0 stubs) · `test-writer` (automate Red Phase Unit tests) · `code-writer` (implement Green Phase logic; mark `[x]` only)

---

## 0. Type Surface & Skeleton Roadmap
Checklist of all DTOs, interfaces, entities, and endpoint stubs that `skeleton-writer` must place on disk before tests begin.

- [x] Implement: DTO `AddressDto` matching SPEC §2.1 and §3.2 with validation constraints
- [x] Implement: DTO `CreateUserRequest` matching SPEC §2.1 and §3.1 with Jakarta Validation constraints
- [x] Implement: DTO `UpdateUserRequest` matching SPEC §2.1, §3.1, and §100-103
- [x] Implement: DTO `UserResponse` matching SPEC §2.1 with `@JsonInclude(JsonInclude.Include.NON_NULL)`
- [x] Implement: DTO `ProblemDetail` (RFC 7807) and `InvalidParam` matching SPEC §4
- [x] Implement: JPA entity `Address` with Panache companion/repository
- [x] Implement: JPA entity `User` with Panache companion/repository (with soft-delete flag, self/parent relationships)
- [x] Implement: Repository interfaces/classes `UserRepository` and `AddressRepository`
- [x] Implement: Service interface & class `UserService` with method stubs throwing `NotImplementedError` or `UnsupportedOperationException`
- [x] Implement: Exception mapper `ConstraintViolationExceptionMapper`, `UserNotFoundExceptionMapper`, `ConflictExceptionMapper`, `CyclicRelationshipExceptionMapper`
- [x] Implement: Resource/Controller `UserResource` (`/api/v1/users`) endpoint stubs throwing `UnsupportedOperationException`

---

## 1. TDD Implementation Roadmap (Step-by-Step Micro-Tasks)

### Task 1.1: Address Normalization & Reusability Logic
- **Step Goal**: Implement address normalization (trim, collapse whitespace, case-insensitive equality) and reuse-or-create logic.
- **Target Signature**: `AddressService#resolveAddress(dto: AddressDto?): Address?`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testResolveAddress_returnsNullWhenDtoIsNull` — returns null when address is omitted
  - [x] Test: `testResolveAddress_reusesExistingAddressWhenAllComponentsMatchCaseInsensitive` — matches existing address when country, city, street, building, apartment, postalCode match after whitespace collapse
  - [x] Test: `testResolveAddress_createsNewAddressWhenAnyComponentDiffers` — creates new record when e.g. postalCode or apartment differs
- **Green Phase Guidance**: Implement `normalizeWhitespace` helper and repository lookup via case-insensitive Panache query, persisting new `Address` only if not found.

### Task 1.2: Email & Phone Normalization and Uniqueness
- **Step Goal**: Implement email lowercase normalization and E.164 phone normalization with active-user uniqueness verification.
- **Target Signature**: `UserService#validateAndNormalizeCredentials(email: String, phone: String?, excludeUserId: Long? = null): Pair<String, String?>`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testNormalizeCredentials_emailLowercasedAndPhoneE164Formatted` — verifies email is lowercased and phone stripped of spaces/dashes into `+\d+`
  - [x] Test: `testValidateCredentials_throwsConflictWhenActiveEmailExists` — throws `ConflictException` when another active user shares the normalized email
  - [x] Test: `testValidateCredentials_throwsConflictWhenActivePhoneExists` — throws `ConflictException` when another active user shares the normalized phone
  - [x] Test: `testValidateCredentials_allowsReusingEmailOrPhoneOfSoftDeletedUser` — allows registration if matching record has `is_deleted = true`
- **Green Phase Guidance**: Normalize strings, query `UserRepository.findActiveByEmail` and `findActiveByPhone`, and throw `ConflictException` if duplicate found on active row.

### Task 1.3: Parent Reference Validation & Ancestry Cycle Prevention
- **Step Goal**: Enforce parental relationship invariants: references must exist and be active, user cannot be own parent, mother != father, and ancestry must be acyclic.
- **Target Signature**: `UserService#validateParentage(userId: Long?, fatherId: Long?, motherId: Long?)`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testValidateParentage_throwsNotFoundWhenParentNotFoundOrSoftDeleted` — throws `UserNotFoundException` if `fatherId` or `motherId` points to non-existent or soft-deleted user
  - [x] Test: `testValidateParentage_throwsBadRequestWhenUserIsOwnParent` — throws `IllegalArgumentException` / `BadParentageException` if `userId == fatherId` or `userId == motherId`
  - [x] Test: `testValidateParentage_throwsBadRequestWhenFatherEqualsMother` — throws `BadParentageException` if `fatherId == motherId`
  - [x] Test: `testValidateParentage_throwsBadRequestWhenCycleDetected` — throws `BadParentageException` if assigning parent would cause an ancestor loop (e.g. child becomes ancestor of parent)
- **Green Phase Guidance**: Validate parent existence and active flag, verify `fatherId != motherId`, and perform recursive ancestor traversal to detect cycles before persisting.

### Task 1.4: Create User (`POST /api/v1/users`)
- **Step Goal**: Implement user creation with demographic fields, address linking, parent linking, and response DTO mapping.
- **Target Signature**: `UserResource#createUser(request: CreateUserRequest): Response` & `UserService#createUser(request: CreateUserRequest): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testCreateUser_successReturns201AndLocationHeader` — verifies 201 Created status, Location header `/api/v1/users/{id}`, and UserResponse body
  - [x] Test: `testCreateUser_validationFailureReturns400ProblemDetails` — asserts 400 Bad Request with RFC 7807 payload when firstName is blank or email is invalid
  - [x] Test: `testCreateUser_conflictReturns409ProblemDetails` — asserts 409 Conflict when active email or phone collides
- **Green Phase Guidance**: In `UserService.createUser`, normalize credentials, resolve address, validate parents, construct `User` entity, persist via repository, and map to `UserResponse`.

### Task 1.5: Get User by ID & Direct Email Lookup
- **Step Goal**: Implement retrieval of active users by primary key and normalized email query.
- **Target Signature**: `UserResource#getUserById(id: Long): UserResponse` & `UserResource#getUserByEmail(email: String): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testGetUserById_successReturns200WithOmittedNulls` — returns 200 with UserResponse; null optional fields omitted from JSON
  - [x] Test: `testGetUserById_notFoundReturns404WhenUserMissingOrSoftDeleted` — returns 404 Not Found Problem Details
  - [x] Test: `testGetUserByEmail_successNormalizesAndReturns200` — lowercases input query param and returns matching active user
  - [x] Test: `testGetUserByEmail_returns404WhenEmailNotFoundOrDeleted` — returns 404 Problem Details
- **Green Phase Guidance**: Query active users by ID or lowercase email, return 404 if missing or soft-deleted, otherwise map entity and active children IDs to `UserResponse`.

### Task 1.6: Update User (`PUT /api/v1/users/{id}`)
- **Step Goal**: Full-resource replacement semantics with individual address mutation and optional field null-resetting.
- **Target Signature**: `UserResource#updateUser(id: Long, request: UpdateUserRequest): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testUpdateUser_successReplacesAllFields` — returns 200 OK with updated profile
  - [x] Test: `testUpdateUser_explicitNullResetsOptionalFields` — sets phone, address, and parent links to null in database when omitted or null in request
  - [x] Test: `testUpdateUser_addressChangeDoesNotMutateSharedAddressRow` — links user to a new/different address row without modifying original shared row
  - [x] Test: `testUpdateUser_returns404WhenTargetUserDoesNotExistOrDeleted` — returns 404 Not Found
- **Green Phase Guidance**: Fetch existing active user (or 404), replace all fields, relink address, revalidate parents and uniqueness (excluding current user ID), and persist changes.

### Task 1.7: Soft Delete User (`DELETE /api/v1/users/{id}`)
- **Step Goal**: Mark user `is_deleted = true`, preserving parental references in foreign records.
- **Target Signature**: `UserResource#deleteUser(id: Long): Response` & `UserService#deleteUser(id: Long)`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testDeleteUser_successReturns204NoContent` — returns 204 with empty body; user marked `is_deleted = true`
  - [x] Test: `testDeleteUser_preservesParentalReferencesInChildren` — child users still retain `father_id` / `mother_id` pointing to soft-deleted parent
  - [x] Test: `testDeleteUser_returns404WhenAlreadyDeletedOrNotFound` — returns 404 Not Found Problem Details
- **Green Phase Guidance**: In `UserService.deleteUser`, locate user (throw 404 if missing or already deleted), set `isDeleted = true`, and save.

### Task 1.8: Get Direct Family (`GET /api/v1/users/{id}/family`)
- **Step Goal**: Return top-level JSON array of active user, active direct parents, and active direct children.
- **Target Signature**: `UserResource#getDirectFamily(id: Long): List<UserResponse>`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testGetFamily_activeUserWithNoRelativesReturnsSelfInArray` — returns 200 OK with `[user]`
  - [x] Test: `testGetFamily_returnsActiveParentsAndActiveChildrenExcludingDeletedRelatives` — returns list of active relatives; soft-deleted parent or child excluded from results
  - [x] Test: `testGetFamily_softDeletedAnchorReturnsOnlyActiveRelatives` — when target user is soft-deleted, returns their active parents and children without the target user
  - [x] Test: `testGetFamily_returns404WhenTargetUserNeverExisted` — returns 404 Not Found Problem Details
- **Green Phase Guidance**: Query user by ID (regardless of `is_deleted` to check existence); if row does not exist throw 404. Collect active target user (if `!isDeleted`), active parents, and active children (`father_id = :id OR mother_id = :id` with `is_deleted = false`), mapping all to `List<UserResponse>`.

### Task 1.9: RFC 7807 Global Exception Mapping
- **Step Goal**: Ensure all application exceptions and validation errors serialize to RFC 7807 `application/problem+json`.
- **Target Signature**: `@Provider ProblemExceptionMapper : ExceptionMapper<Exception>`
- **Red Phase (Unit Tests)**:
  - [x] Test: `testExceptionMapper_mapsConstraintViolationTo400WithInvalidParams` — maps Jakarta constraint violations to 400 Problem Details with `invalidParams` list
  - [x] Test: `testExceptionMapper_mapsNotFoundTo404` — maps `UserNotFoundException` to 404 Problem Details
  - [x] Test: `testExceptionMapper_mapsConflictTo409` — maps `ConflictException` to 409 Problem Details
  - [x] Test: `testExceptionMapper_mapsBadParentageTo400` — maps `BadParentageException` to 400 Problem Details
- **Green Phase Guidance**: Implement Quarkus `@ServerExceptionMapper` or JAX-RS `ExceptionMapper` classes formatting `ProblemDetail`.

---

## 2. Deferred Integration & E2E Backlog
Checklist of deferred integration scenarios for future phases after unit TDD cycle completion.

- [x] Integration: Flyway schema migration application (`V1__init_schema.sql`) and index verification on H2
- [x] Integration: Flyway seed dataset verification (`V2__seed_users.sql`): exactly 1,000 active users, 20 distinct addresses, parental age invariants (`birthDate(parent) < birthDate(child)`)
- [x] Integration: Multi-container orchestration verification via Docker Compose (`user-service` + standalone `h2-db` TCP server)
- [x] E2E: Full end-to-end user scenario through the deployed HTTP interface (Create -> Retrieve -> Family lookup -> Soft delete -> Verify exclusion)
