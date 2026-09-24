# TDD Plan: User Service — CRUD, Direct Lookup & Family Graph

## Overview
- **Feature Identifier**: `user-service`
- **Source Contract**: `docs/features/user-service/SPEC.md`
- **Engineering Standards**: `.ai/guidelines/engineering-standards.md`
- **Author**: `tdd-planner` (sole owner of task wording, order, and structure)
- **Consumers**:
  - `skeleton-writer` — implements Section 0 and marks only Section 0 checkboxes
  - `test-writer` — writes Section 1 Red tests and marks only `Red Test` checkboxes
  - `code-writer` — implements Section 1 Green guidance and marks only `Green` checkboxes
- **Active TDD Scope**: in-memory Unit tests and isolated Spring MVC Slice tests only
- **Excluded from Active Cycle**: all Section 2 database, Flyway, Docker, network, and E2E scenarios

### Checkbox Ownership Contract
- `[ ] Skeleton:` → `skeleton-writer` may change to `[x]`.
- `[ ] Red Test:` → `test-writer` may change to `[x]` after the test exists, compiles, and fails only on a `Not Implemented` production stub.
- `[ ] Green:` → `code-writer` may change to `[x]` only after the related test group passes.
- No downstream agent may alter wording, task order, headings, or Section 2 checkboxes.

---

## 0. Type Surface & Skeleton Roadmap

All artifacts use base package `com.userservice`. Method bodies in concrete service/controller/error-handler skeletons must throw `UnsupportedOperationException("Not implemented")`. Entities contain persistence mapping only—no business behavior.

### 0.1 Application Entry Point
- [x] Skeleton: Create `com.userservice.UserServiceApplication` with `@SpringBootApplication` and a standard `main(String[] args)` entry point.

### 0.2 DTO Contracts
- [x] Skeleton: Create `AddressDto(Long id, String country, String city, String street, String building, String apartment, String postalCode)`; `id` is response-only, required address fields carry Jakarta validation, and null optional fields are omitted from JSON.
- [x] Skeleton: Create `UserCreateRequest(String firstName, String lastName, LocalDate birthDate, String email, String phoneNumber, AddressDto address, Long fatherId, Long motherId)` with SPEC validation and `@Valid` nested address.
- [x] Skeleton: Create `UserUpdateRequest` with the same replacement fields and validation surface as `UserCreateRequest`.
- [x] Skeleton: Create `UserResponse(Long id, String firstName, String lastName, LocalDate birthDate, String email, String phoneNumber, AddressDto address, Long fatherId, Long motherId, List<Long> childrenIds)`; omit null optionals and always emit `childrenIds`.

### 0.3 Persistence Types
- [x] Skeleton: Create JPA `Address` mapped to `addresses` with `id`, `country`, `city`, `street`, `building`, nullable `apartment`, and nullable `postalCode`.
- [x] Skeleton: Create JPA `User` mapped to `users` with `id`, demographics, normalized email/phone, nullable many-to-one `Address`, nullable self-referencing `father`/`mother`, and `isDeleted`.

### 0.4 Repository Contracts
- [x] Skeleton: Create `AddressRepository extends JpaRepository<Address, Long>` with `Optional<Address> findMatchingNormalized(String country, String city, String street, String building, String apartment, String postalCode)`.
- [x] Skeleton: Create `UserRepository extends JpaRepository<User, Long>` with `Optional<User> findByIdAndIsDeletedFalse(Long id)`, `Optional<User> findByEmailAndIsDeletedFalse(String email)`, and inherited `findById(Long id)` for family-anchor resolution.
- [x] Skeleton: Add active uniqueness signatures: `existsByEmailAndIsDeletedFalse`, `existsByPhoneNumberAndIsDeletedFalse`, plus ID-excluding variants for PUT.
- [x] Skeleton: Add `List<User> findActiveChildren(Long parentId)` for `(father_id = :id OR mother_id = :id) AND is_deleted = false`.

### 0.5 Service Type Surface
- [x] Skeleton: Create `UserService` interface with:
  - `UserResponse create(UserCreateRequest request)`
  - `UserResponse getById(Long id)`
  - `UserResponse update(Long id, UserUpdateRequest request)`
  - `void softDelete(Long id)`
  - `UserResponse getByEmail(String email)`
  - `List<UserResponse> getDirectFamily(Long id)`
- [x] Skeleton: Create `UserServiceImpl` implementing every `UserService` method; inject `UserRepository` and `AddressRepository`; every method throws `UnsupportedOperationException("Not implemented")`.

### 0.6 Error Contracts
- [x] Skeleton: Create domain API exception types for user absence, parent absence, duplicate email, duplicate phone, invalid phone input, and invalid parent relationship; no mapping logic yet.
- [x] Skeleton: Create RFC 7807 validation item DTO `InvalidParam(String name, String reason)`.
- [x] Skeleton: Create `ApiExceptionHandler` (`@RestControllerAdvice`) signatures for domain exceptions, method-argument validation, and malformed JSON; every handler throws `UnsupportedOperationException("Not implemented")`.

### 0.7 HTTP Endpoint Stubs
- [x] Skeleton: Create `UserController` at `/api/v1/users`, inject `UserService`, and expose:
  - `POST /api/v1/users`
  - `GET /api/v1/users/{id}`
  - `PUT /api/v1/users/{id}`
  - `DELETE /api/v1/users/{id}`
  - `GET /api/v1/users/by-email?email=...`
  - `GET /api/v1/users/{id}/family`
- [x] Skeleton: Apply exact request validation, parameter bindings, DTO-only return types, and success response signatures; every handler throws `UnsupportedOperationException("Not implemented")`.

### 0.8 Skeleton Gate
- [x] Skeleton: Run the exact `Compile / Build Check` command from `docs/PROJECT_ENV.md`; confirm zero syntax, type, package, or import errors without writing tests or business logic.

---

## 1. TDD Implementation Roadmap (Step-by-Step Micro-Tasks)

Execute tasks in order. Every mocked collaborator must be configured with valid scenario-specific values. No Red test may fail because a mock returned an accidental `null`, because Spring context configuration is broken, or because test data is invalid.

### Task 1.1 — Create: Minimal User, Email Normalization & Persisted Response
- **Step Goal**: Implement minimal user creation, lowercase email normalization, save, reload/map, and empty `childrenIds`.
- **Target Signature**: `UserServiceImpl#create(UserCreateRequest): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `create_requiredFields_normalizesEmailAndReturnsPersistedUser` — configure uniqueness checks `false`, `save` to return an entity with generated ID, and active children `[]`; assert lowercase email and generated ID.
  - [x] Red Test: `create_requiredFields_keepsAbsentOptionalsOmittedAndChildrenEmpty` — assert phone/address/parents are null in the DTO model and `childrenIds=[]`.
- **Mock Behavior Guidance**: Stub every repository call (`exists`, `save`, `findActiveChildren`) with non-null values. The initial failure must be `UnsupportedOperationException` from `UserServiceImpl#create`.
- **Green Phase Guidance**:
  - [x] Green: Normalize email using locale-independent lowercase, create an active entity, save it, and map the persisted entity to `UserResponse`.

### Task 1.2 — Create: Phone Normalization, Validation & Active Uniqueness
- **Step Goal**: Normalize formatted phone input to E.164 and enforce active email/phone uniqueness.
- **Target Signature**: `UserServiceImpl#create(UserCreateRequest): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `create_formattedPhone_persistsCanonicalE164` — formatted input with spaces/parentheses/separators becomes `+{countryCode}{number}`.
  - [x] Red Test: `create_nonNormalizablePhone_throwsValidationFailure` — letters, missing `+`, country code starting zero, or normalized length outside 7–15 digits is rejected.
  - [x] Red Test: `create_duplicateNormalizedEmail_throwsDuplicateEmail` — repository reports active lowercase collision; save is never called.
  - [x] Red Test: `create_duplicateNormalizedPhone_throwsDuplicatePhone` — different formatting normalizes to an active collision; save is never called.
  - [x] Red Test: `create_identityUsedOnlyByDeletedUser_isAllowed` — active existence checks return false and save succeeds.
- **Mock Behavior Guidance**: Stub both email and phone existence checks explicitly in every happy/conflict scenario.
- **Green Phase Guidance**:
  - [x] Green: Add canonical phone normalization/validation and active-only conflict checks before persistence.

### Task 1.3 — Address Match-or-Insert & Normalized Identity
- **Step Goal**: Normalize address whitespace, compare all six fields case-insensitively, reuse exact matches, and insert on any difference.
- **Target Signature**: `UserServiceImpl#create` and `UserServiceImpl#update`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `create_addressWhitespaceAndCaseVariant_reusesExistingAddress` — repository match returns an existing address; `AddressRepository#save` is never called.
  - [x] Red Test: `create_anyAddressDetailDiffers_insertsNewAddress` — include a postal-code-only difference; match is empty and save returns a generated address.
  - [x] Red Test: `update_newAddress_relinksOnlyTargetAndNeverMutatesOldAddress` — user save receives the new link; no old-address save/update/delete occurs.
  - [x] Red Test: `update_nullAddress_unlinksTarget` — replacement writes null `address`.
- **Mock Behavior Guidance**: Stub exact address match and address save outcomes; stub user save and active children for response mapping.
- **Green Phase Guidance**:
  - [x] Green: Implement trim/whitespace collapse, case-insensitive six-field match lookup, match-or-insert, and user relinking without mutating address rows.

### Task 1.4 — Parent Resolution & Basic Relationship Validation
- **Step Goal**: Resolve active parents and reject missing, soft-deleted, same-role, and self-parent assignments.
- **Target Signature**: `UserServiceImpl#create` and `UserServiceImpl#update`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `create_activeParents_linksFatherAndMother`.
  - [x] Red Test: `create_missingOrDeletedParent_throwsParentNotFound`.
  - [x] Red Test: `create_sameFatherAndMother_throwsInvalidParentRelationship`.
  - [x] Red Test: `update_selfAsFatherOrMother_throwsInvalidParentRelationship`.
  - [x] Red Test: `update_nullParents_unlinksBothParents`.
- **Mock Behavior Guidance**: Stub parent active lookups with explicit `Optional.of(...)` / `Optional.empty()` and stub all unrelated uniqueness/address calls.
- **Green Phase Guidance**:
  - [x] Green: Resolve active parents before save; reject equal parent IDs and target self-reference; apply null replacement semantics.

### Task 1.5 — Parent Graph Cycle Prevention
- **Step Goal**: Reject direct and indirect ancestry cycles during PUT.
- **Target Signature**: `UserServiceImpl#update(Long, UserUpdateRequest): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `update_childAsParent_rejectsDirectCycle`.
  - [x] Red Test: `update_descendantAsParent_rejectsIndirectCycle`.
  - [x] Red Test: `update_unrelatedActiveParent_allowsAssignment`.
- **Mock Behavior Guidance**: Build finite in-memory entity parent graphs and return them from active parent lookup; never rely on a database or lazy-loading proxy.
- **Green Phase Guidance**:
  - [x] Green: Traverse parent chains with a visited-ID set and reject any path that reaches the target; terminate safely on repeated nodes.

### Task 1.6 — User Response Mapping & Deleted-Relation Redaction
- **Step Goal**: Map entities to DTOs without exposing entities or soft-deleted relation IDs.
- **Target Signature**: response mapping used by all `UserServiceImpl` read/write methods
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `response_activeParents_includesNumericParentIds`.
  - [x] Red Test: `response_deletedParent_omitsCorrespondingParentId`.
  - [x] Red Test: `response_children_containsOnlyActiveDirectChildIds`.
  - [x] Red Test: `response_nullOptionalsRemainNullForJsonOmission`.
- **Mock Behavior Guidance**: Stub `findActiveChildren` with explicit active child entities and configure parent deletion flags.
- **Green Phase Guidance**:
  - [x] Green: Implement DTO mapping with active relation filtering and non-null `childrenIds`.

### Task 1.7 — Direct Lookup by ID
- **Step Goal**: Return only an active user by ID.
- **Target Signature**: `UserServiceImpl#getById(Long): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `getById_activeUser_returnsMappedResponse`.
  - [x] Red Test: `getById_missingOrDeletedUser_throwsUserNotFound`.
- **Mock Behavior Guidance**: Stub active lookup and children query; never leave repository return values unconfigured.
- **Green Phase Guidance**:
  - [x] Green: Use active-only repository lookup and map the entity; translate empty result to user-not-found.

### Task 1.8 — Direct Lookup by Normalized Email
- **Step Goal**: Perform lowercase exact active-user lookup.
- **Target Signature**: `UserServiceImpl#getByEmail(String): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `getByEmail_mixedCaseInput_usesLowercaseExactLookup`.
  - [x] Red Test: `getByEmail_missingOrDeletedUser_throwsUserNotFound`.
- **Mock Behavior Guidance**: Stub email lookup with explicit normalized argument expectations and active children.
- **Green Phase Guidance**:
  - [x] Green: Lowercase input, call exact active lookup, and map or throw user-not-found.

### Task 1.9 — PUT Full Replacement & Identity Conflicts
- **Step Goal**: Replace all mutable fields, reset omitted optionals, and enforce ID-excluding active uniqueness.
- **Target Signature**: `UserServiceImpl#update(Long, UserUpdateRequest): UserResponse`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `update_completeRequest_replacesRequiredFieldsAndNormalizesIdentity`.
  - [x] Red Test: `update_omittedOptionals_resetsPhoneAddressAndParentsToNull`.
  - [x] Red Test: `update_sameNormalizedIdentityOnCurrentUser_isAllowed`.
  - [x] Red Test: `update_emailUsedByAnotherActiveUser_throwsDuplicateEmail`.
  - [x] Red Test: `update_phoneUsedByAnotherActiveUser_throwsDuplicatePhone`.
  - [x] Red Test: `update_missingOrDeletedTarget_throwsUserNotFound`.
- **Mock Behavior Guidance**: Stub ID-excluding existence methods; configure current target, save result, and child list.
- **Green Phase Guidance**:
  - [x] Green: Load active target, overwrite complete state, reset null optionals, run ID-excluding checks, save, and map.

### Task 1.10 — Soft Delete without Family-Link Mutation
- **Step Goal**: Mark only the active target deleted and preserve family references.
- **Target Signature**: `UserServiceImpl#softDelete(Long): void`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `softDelete_activeUser_setsDeletedAndSavesSameEntity`.
  - [x] Red Test: `softDelete_doesNotDeleteEntityOrRewriteRelatives`.
  - [x] Red Test: `softDelete_missingOrAlreadyDeleted_throwsUserNotFound`.
- **Mock Behavior Guidance**: Stub active lookup; verify `save` and verify no repository `delete*` or unrelated-user save calls.
- **Green Phase Guidance**:
  - [x] Green: Set `isDeleted=true` and save only the target; never physically delete or clear links.

### Task 1.11 — Direct Family Assembly
- **Step Goal**: Assemble active anchor, active parents, and active children with no recursion and no ordering guarantee.
- **Target Signature**: `UserServiceImpl#getDirectFamily(Long): List<UserResponse>`
- **Red Phase (Unit Tests)**:
  - [x] Red Test: `getDirectFamily_activeAnchor_returnsAnchorActiveParentsAndActiveChildren`.
  - [x] Red Test: `getDirectFamily_activeAnchorWithoutRelatives_returnsAnchorOnly`.
  - [x] Red Test: `getDirectFamily_deletedAnchor_returnsOnlyActiveDirectRelatives`.
  - [x] Red Test: `getDirectFamily_deletedAnchorWithoutActiveRelatives_returnsEmptyList`.
  - [x] Red Test: `getDirectFamily_neverExistingAnchor_throwsUserNotFound`.
  - [x] Red Test: `getDirectFamily_excludesDeletedRelativesAndRecursiveAncestors`.
- **Mock Behavior Guidance**: Use inherited `findById` for the anchor (including deleted), explicit parent deletion flags, and a configured active-child list.
- **Green Phase Guidance**:
  - [x] Green: Resolve any existing anchor row, select only active direct members, de-duplicate by ID, and map to a top-level list.

### Task 1.12 — Controller Success Contracts
- **Step Goal**: Implement all six HTTP mappings with exact success status, body, and Location behavior.
- **Target Signatures**: all `UserController` endpoint methods from Section 0.7
- **Red Phase (Spring MVC Slice Tests)**:
  - [x] Red Test: `postUsers_validBody_returns201LocationAndPersistedDto`.
  - [x] Red Test: `getUserById_returns200Dto`.
  - [x] Red Test: `putUser_validReplacement_returns200Dto`.
  - [x] Red Test: `deleteUser_returns204AndEmptyBody`.
  - [x] Red Test: `getUserByEmail_passesQueryAndReturns200Dto`.
  - [x] Red Test: `getDirectFamily_returnsTopLevelJsonArray`.
- **Anti-Deadlock Mock Guidance**: For every happy path, preconfigure the mocked `UserService` with a complete non-null `UserResponse`, list, or no-op. Red failure must originate from the controller's `UnsupportedOperationException`, never a null mock.
- **Green Phase Guidance**:
  - [x] Green: Delegate controller methods to `UserService`, construct Location from persisted ID, and return exact HTTP responses.

### Task 1.13 — Request Validation & JSON Omission
- **Step Goal**: Enforce DTO validation at the HTTP boundary and response serialization rules.
- **Target Signatures**: `UserController#create`, `UserController#update`, DTO validation annotations
- **Red Phase (Spring MVC Slice Tests)**:
  - [x] Red Test: `postOrPut_blankNamesInvalidEmailOrFutureBirthDate_returns400ProblemDetails`.
  - [x] Red Test: `postOrPut_oversizedAddressFields_returns400WithInvalidParams`.
  - [x] Red Test: `postOrPut_invalidPhoneAfterNormalization_returns400ProblemDetails`.
  - [x] Red Test: `response_nullOptionals_areOmittedAndChildrenIdsIsArray`.
- **Anti-Deadlock Mock Guidance**: For Bean Validation cases verify service is never called; for service-level normalized-phone failure preconfigure service to throw the intended validation exception.
- **Green Phase Guidance**:
  - [x] Green: Apply Jakarta constraints, nested validation, null omission configuration, and stable validation Problem Details.

### Task 1.14 — RFC 7807 Domain Error Mapping
- **Step Goal**: Map service exceptions to exact RFC 7807 contracts.
- **Target Signature**: `ApiExceptionHandler` domain-exception handlers
- **Red Phase (Spring MVC Slice Tests)**:
  - [x] Red Test: `userNotFound_returns404UserNotFoundProblem`.
  - [x] Red Test: `parentNotFound_returns404ParentUserNotFoundProblem`.
  - [x] Red Test: `duplicateEmail_returns409EmailConflictProblem`.
  - [x] Red Test: `duplicatePhone_returns409PhoneConflictProblem`.
  - [x] Red Test: `invalidParentRelationship_returns400InvalidParentProblem`.
- **Anti-Deadlock Mock Guidance**: Configure the mocked service method for each endpoint to throw the exact domain exception. No mock may return null.
- **Green Phase Guidance**:
  - [x] Green: Produce `application/problem+json` with exact `type`, `title`, `status`, `detail`, and request `instance`.

### Task 1.15 — RFC 7807 Validation & Malformed JSON Mapping
- **Step Goal**: Map field validation and unreadable JSON to stable Problem Details.
- **Target Signature**: `ApiExceptionHandler` validation and malformed-payload handlers
- **Red Phase (Spring MVC Slice Tests)**:
  - [x] Red Test: `beanValidationFailure_returns400WithInvalidParamsNameAndReason`.
  - [x] Red Test: `malformedJsonOrInvalidDate_returns400MalformedJsonProblem`.
- **Anti-Deadlock Mock Guidance**: Validation/malformed requests must fail before service invocation; verify zero service interactions.
- **Green Phase Guidance**:
  - [x] Green: Collect field errors into `invalidParams`; map unreadable payloads to `urn:user-service:error:malformed-json`.

### Task 1.16 — Unit TDD Completion Gate
- **Step Goal**: Establish the immutable Green handoff.
- **Red Phase Verification**: `test-writer` must confirm every Section 1 test compiles and initially fails only on a target `Not Implemented` stub before Green implementation begins; this is a suite gate, not an additional test method.
- **Green Verification**:
  - [x] Green: Run the exact `Run All Tests` command from `docs/PROJECT_ENV.md` and confirm 0 failures / 0 errors without modifying `src/test/**`.

---

## 2. Deferred Integration & E2E Backlog

These items are intentionally outside Steps 05–06. `test-writer` and `code-writer` must not implement or mark them.

### 2.1 Database & Repository Integration
- [ ] Deferred Integration: Apply `V1__init_schema.sql` to a real H2 2.2.224 database and verify tables, FKs, generated active-key columns, and indexes.
- [ ] Deferred Integration: Verify active email/phone uniqueness at database level and reuse after one or multiple soft deletions.
- [ ] Deferred Integration: Verify `UserRepository` active lookups exclude soft-deleted rows.
- [ ] Deferred Integration: Verify `findActiveChildren` implements `(father_id=:id OR mother_id=:id) AND is_deleted=false`.
- [ ] Deferred Integration: Verify normalized case-insensitive six-field address matching under concurrent inserts.
- [ ] Deferred Integration: Verify Hibernate `ddl-auto=validate` accepts the Flyway schema.

### 2.2 Flyway Seed Verification
- [ ] Deferred Integration: Apply `V2__seed_1000_users.sql` and verify exactly 20 addresses and 1,000 active users.
- [ ] Deferred Integration: Verify address distribution is uneven and every address has 10–120 users.
- [ ] Deferred Integration: Verify all seed parent IDs exist and every parent birth date is earlier than the child birth date.
- [ ] Deferred Integration: Verify the seed contains grandparents, parents, and children and identity sequences restart at 21 / 1001.

### 2.3 Container & Runtime Integration
- [ ] Deferred Integration: Build both Docker images and verify H2 health check readiness.
- [ ] Deferred Integration: Start `docker-compose.yml` and verify `user-service` connects to `h2-db:9092`.
- [ ] Deferred Integration: Verify Flyway runs automatically on application startup in local and Docker profiles.
- [ ] Deferred Integration: Verify persistent H2 volume retains data across container restart.

### 2.4 End-to-End HTTP Scenarios
- [ ] Deferred E2E: Execute create → get by ID → get by normalized email → PUT replacement → family retrieval → soft delete through a deployed HTTP server.
- [ ] Deferred E2E: Verify `Location`, RFC 7807 content type, JSON omission, and soft-deleted identity reuse over the network.
- [ ] Deferred E2E: Verify soft-deleted family anchor behavior against persisted multi-generation data.

---

## 3. Assumption Traceability

| Assumption | Affected Roadmap | Required Attention |
|---|---|---|
| `[AI-ASSUMPTION: Standard boilerplate reduction]` | Section 0 entities/DTOs | Lombok may remove boilerplate only; it must not hide business behavior. |
| `[AI-ASSUMPTION: Concrete compatible tool patch versions required for deterministic manifests]` | Section 2 infrastructure verification | Validate Spring Boot 3.3.6, Gradle 8.10.2, Flyway 10.10.0, and H2 2.2.224 together during deferred integration. |

---

## 4. Definition of Done & Handoff
1. Section 0 lists every DTO, entity, repository/service interface, exception contract, entry point, and endpoint stub needed by downstream tests.
2. Section 1 covers all six endpoints, normalization rules, address identity, parent invariants/cycles, soft delete, family behavior, JSON omission, and RFC 7807 mappings from `SPEC.md`.
3. Section 1 contains Unit/Slice scenarios only; no live DB, Docker, Flyway execution, or network E2E test is active.
4. Every mocked dependency has explicit anti-deadlock guidance.
5. Section 2 contains all deferred integration and E2E scenarios.
6. No source, test, build, environment, PRD, or SPEC file was modified by `tdd-planner`.
7. Handoff order: `environment-bootstrap.md` → `skeleton-writer.md` (Section 0) → `test-writer.md` (Section 1 Red tests) → `code-writer.md` (Section 1 Green).
