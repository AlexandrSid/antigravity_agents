# Feature Specification: User Service (Core CRUD, Search & Demographic Graph)

Binding constraints: `docs/PRD.md` (`Scope: [TARGET]` only) and `.ai/guidelines/engineering-standards.md`. `[BACKLOG]` items are out of scope.

## 1. Overview & Communication Pattern
- **Feature Identifier**: `user-service`
- **Target Scope**: CRUD for users, fuzzy multi-criteria search with pagination, soft-delete lifecycle, normalized shared addresses, and Flyway seed of 1,000 interconnected users across 20 locations.
- **Communication Pattern**: Synchronous Request/Reply (REST HTTP/JSON, from PRD §1.1).
- **Participants**:
  - API Consumers / External Clients
  - `UserController` — HTTP boundary, Jakarta Validation, DTO mapping only
  - `UserService` — domain invariants, uniqueness, soft delete, address resolve/relink
  - `UserRepository`, `AddressRepository` — persistence
  - Data Store: H2 in TCP Server mode (`h2-db` Docker service)

### 1.1 Architecture Constraints (Engineering Standards)
- Strict 3-tier layering: Controller → Service → Repository. No layer skipping.
- JPA entities (`User`, `Address`) are never serialized in API responses. Controller I/O is DTO-only: `UserCreateRequest`, `UserUpdateRequest`, `UserResponse`, `AddressDto`.
- Configuration is split by environment: `application.yml` (default/local), `application-docker.yml` (Compose).
- Stack is fixed by PRD §1.1: Java 21, Gradle Kotlin DSL, Spring Boot 3.3.x (Web, Data JPA, Jakarta Validation), Flyway, Lombok `[AI-ASSUMPTION: Standard boilerplate reduction | Attention Needed for Implementer]`, H2 TCP, Docker Compose (`user-service` + `h2-db`). No unapproved technologies.

---

## 2. Interface & Interaction Contract
- **Target Identifier / Location**: `/api/v1/users`
- **Interaction Semantics**: Synchronous Request/Response
- **Transport & Wire Format**: HTTP, `Content-Type: application/json` on success; errors use `Content-Type: application/problem+json` (RFC 7807).

### 2.1 Inbound / Outbound Schemas

#### 1. Create User
- **Method & Path**: `POST /api/v1/users`
- **Inbound DTO**: `UserCreateRequest`
- **Success Status**: `201 Created`
- **Headers**: `Location: /api/v1/users/{id}`
- **Outbound DTO**: `UserResponse`
- **Request Body**:
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "birthDate": "1990-05-15",
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "address": {
    "country": "USA",
    "city": "Springfield",
    "street": "Main St",
    "building": "42",
    "apartment": "10A",
    "postalCode": "12345"
  },
  "fatherId": 12,
  "motherId": 15
}
```
- Optional inbound fields: `phoneNumber`, `address`, `fatherId`, `motherId`. Inside `address`, `apartment` and `postalCode` are optional.
- Address resolve on create: if country + city + street + building + apartment match an existing row, link `address_id` to that row; otherwise insert a new `Address`. Never mutate an existing address row.
- Soft-deleted identity reuse: an `email` or `phoneNumber` that exists only on `is_deleted = true` rows is allowed for a new active user (not a `409`).
- **Response Body (`201 Created`)**:
```json
{
  "id": 1001,
  "firstName": "John",
  "lastName": "Doe",
  "birthDate": "1990-05-15",
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "isDeleted": false,
  "address": {
    "id": 5,
    "country": "USA",
    "city": "Springfield",
    "street": "Main St",
    "building": "42",
    "apartment": "10A",
    "postalCode": "12345"
  },
  "fatherId": 12,
  "motherId": 15,
  "childrenIds": []
}
```

---

#### 2. Get User by ID
- **Method & Path**: `GET /api/v1/users/{id}`
- **Success Status**: `200 OK`
- **Outbound DTO**: `UserResponse` (same shape as Create). Includes address, `fatherId`, `motherId`, and `childrenIds` of direct active children.
- Soft-deleted and missing users are indistinguishable: RFC 7807 `404`.

---

#### 3. Update User (PUT Full-Replacement)
- **Method & Path**: `PUT /api/v1/users/{id}`
- **Inbound DTO**: `UserUpdateRequest`
- **Success Status**: `200 OK`
- **Outbound DTO**: `UserResponse`
- **Request Body**:
```json
{
  "firstName": "Jonathan",
  "lastName": "Doe",
  "birthDate": "1990-05-15",
  "email": "jonathan.doe@example.com",
  "phoneNumber": null,
  "address": {
    "country": "USA",
    "city": "Springfield",
    "street": "Oak St",
    "building": "7",
    "apartment": null,
    "postalCode": "12346"
  },
  "fatherId": null,
  "motherId": 15
}
```
- **PUT Null-Handling (Engineering Standards)**:
  - `PUT` is full-resource replacement, not a partial patch. Required fields (`firstName`, `lastName`, `birthDate`, `email`) must be present and valid.
  - JSON `null` on optional fields **explicitly resets** the corresponding user columns to SQL `NULL`: `phoneNumber`, `fatherId`, `motherId`, `address`.
  - Omitted optional fields are treated as `null` (replacement, not merge).
  - `address: null` unlinks the user (`address_id = NULL`). The previous address row is left unchanged.
  - When an `address` object is provided, including `apartment: null` / `postalCode: null`, those nulls are part of the **new** address identity. Resolve by match-or-insert (country, city, street, building, apartment). **Never UPDATE an existing address row** — including the row currently linked to this user — even if it is not shared.
  - Address reassignment is individual: other users keep their existing `address_id`.
- Soft-deleted target: `404` (same as missing).
- Soft-deleted identity reuse on update: changing email/phone to a value used only by soft-deleted rows is allowed (not a `409`).

---

#### 4. Soft Delete User
- **Method & Path**: `DELETE /api/v1/users/{id}`
- **Success Status**: `204 No Content`
- **Behavior**: Sets `is_deleted = true`. Never executes a physical SQL `DELETE`. Soft-deleted users are excluded from GET-by-id and search immediately.
- Missing or already soft-deleted target: RFC 7807 `404`.

---

#### 5. Multi-Criteria Dynamic Search
- **Method & Path**: `GET /api/v1/users`
- **Success Status**: `200 OK`
- Result set is always restricted to `is_deleted = false`.
- **Query Parameters**:
  - `name` (String, optional): Case-insensitive substring (`LIKE %value%`) on `firstName` OR `lastName`.
  - `email` (String, optional): Case-insensitive substring on `email`.
  - `phone` (String, optional): Case-insensitive substring on `phoneNumber`.
  - `familyMemberId` (Long, optional): Active users for whom the given ID is father, mother, or child. If the ID is missing, soft-deleted, or has no active relatives: `200 OK` with empty page (`content: []`, `totalElements: 0`).
  - `country`, `city`, `street`, `building`, `postalCode` (String, optional): Case-insensitive substring on address fields.
  - `page` (Integer, default `0`), `size` (Integer, default `10`), `sort` (String, default `id,asc`).
- **Response Body (`200 OK`)**:
```json
{
  "content": [
    {
      "id": 1001,
      "firstName": "John",
      "lastName": "Doe",
      "birthDate": "1990-05-15",
      "email": "john.doe@example.com",
      "phoneNumber": "+1234567890",
      "isDeleted": false,
      "address": {
        "id": 5,
        "country": "USA",
        "city": "Springfield",
        "street": "Main St",
        "building": "42",
        "apartment": "10A",
        "postalCode": "12345"
      },
      "fatherId": 12,
      "motherId": 15,
      "childrenIds": []
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "offset": 0
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

### 2.2 Side-Effects, State Mutations & Events
- **Persistence Changes**:
  - **`users`**:
    - `INSERT` (POST): new id, demographics, optional `phone_number` / `father_id` / `mother_id` / `address_id`, `is_deleted = false`.
    - `UPDATE` (PUT): overwrite replacement state. JSON `null` writes SQL `NULL` on optional user columns. Never physically deletes the row.
    - `SOFT DELETE` (DELETE): `UPDATE users SET is_deleted = true`. Never physical `DELETE`.
  - **Partial unique indexes** (Flyway `V1__init_schema.sql`) — uniqueness only among active rows; enables reuse after soft delete:
    ```sql
    CREATE UNIQUE INDEX idx_users_email_active ON users(email) WHERE is_deleted = false;
    CREATE UNIQUE INDEX idx_users_phone_active ON users(phone_number) WHERE is_deleted = false AND phone_number IS NOT NULL;
    ```
  - **`addresses`**:
    - Match key: country, city, street, building, apartment (PRD Rule 3). `postalCode` is not part of the match key.
    - Match → reuse existing `id`. No match → `INSERT` new row.
    - **Never UPDATE or DELETE address rows** as a side-effect of user create/update/delete.
  - **Flyway**:
    - `V1__init_schema.sql`: `addresses`, `users`, FKs, partial unique indexes.
    - `V2__seed_1000_users.sql`: exactly 20 addresses and 1,000 active users; parents inserted before children; `birthDate(parent) < birthDate(child)` in seed only `[AI-ASSUMPTION: Seed script chronological sanity | Attention Needed for Implementer]`. Runtime parental-age checks are `[BACKLOG]` and must not be implemented.
- **Emitted Events / Messages**: None in `[TARGET]`.
- **External Integration Side-Effects**: None in `[TARGET]`. Compose starts `user-service` and `h2-db` only.

---

## 3. Domain Model & Validation Matrix

DTO field constraints below apply at the Controller. Persistence mapping is Service/Repository responsibility. Entities are not API types.

| Entity / DTO field | Field / Property | Type | Required | Constraints & Domain Invariants | Status / Traceability |
|---|---|---|---|---|---|
| `User` | `id` | `Long` | No (Generated) | Primary key, auto-increment | Confirmed |
| `User` | `firstName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `User` | `lastName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `User` | `birthDate` | `LocalDate` | Yes | `@NotNull`, `@Past` | Confirmed |
| `User` | `email` | `String` | Yes | `@NotBlank`, `@Email`. Unique among `is_deleted = false` only (`idx_users_email_active`). Reusable after soft delete. | Confirmed |
| `User` | `phoneNumber` | `String` | No | `@Pattern` standard phone `[AI-ASSUMPTION: Pattern ^\\+?[0-9. ()-]{7,25}$ \| Attention Needed for Implementer]`. Unique among active non-null phones (`idx_users_phone_active`). Reusable after soft delete. JSON `null` on PUT → SQL `NULL`. | Confirmed + assumption |
| `User` | `isDeleted` | `Boolean` | Yes | Default `false`. Soft-delete flag. Not writable via create/update body. | Confirmed |
| `User` | `address` | `Address` / `AddressDto` | No | FK `address_id`. Shared by reference. Resolve match-or-insert; never mutate existing address rows. PUT `address: null` unlinks only. | Confirmed |
| `User` | `father` | `User` | No | FK `father_id`. Must reference existing active user. Cannot equal self. PUT `null` → SQL `NULL`. | Confirmed |
| `User` | `mother` | `User` | No | FK `mother_id`. Must reference existing active user. Cannot equal self. PUT `null` → SQL `NULL`. | Confirmed |
| `User` | `children` | `List<Long>` (`childrenIds` in DTO) | No (Derived) | Active children only: `WHERE (father_id = :id OR mother_id = :id) AND is_deleted = false`. `[AI-ASSUMPTION: Children computed on demand to avoid recursion/cycle serialization \| Attention Needed for Implementer]` | Confirmed + assumption |
| `Address` | `id` | `Long` | No (Generated) | Primary key, auto-increment. Exposed only inside `AddressDto`. | Confirmed |
| `Address` | `country` | `String` | Yes | `@NotBlank`, `@Size(max=100)` | Confirmed |
| `Address` | `city` | `String` | Yes | `@NotBlank`, `@Size(max=100)` | Confirmed |
| `Address` | `street` | `String` | Yes | `@NotBlank`, `@Size(max=150)` | Confirmed |
| `Address` | `building` | `String` | Yes | `@NotBlank`, `@Size(max=50)` | Confirmed |
| `Address` | `apartment` | `String` | No | `@Size(max=50)`. On PUT, `null` is part of the new address identity (match-or-insert), not an in-place update of a shared row. | Confirmed |
| `Address` | `postalCode` | `String` | No | `@Size(max=20)`. Same PUT identity rule; not part of the address match key. | Confirmed |

---

## 4. Failure Protocol & Error Dynamics

All errors are RFC 7807 Problem Details (`Content-Type: application/problem+json`) with required fields `type`, `title`, `status`, `detail`, `instance`. Validation failures add `invalidParams` (`name` + `reason`). Status mapping is locked by engineering standards: `400` validation/malformed/self-parent; `404` missing or soft-deleted target or parent; `409` active email/phone collision.

### 4.1 Example: Validation Failure (`400`)
```json
{
  "type": "https://api.example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Input validation failed for 1 field(s).",
  "instance": "/api/v1/users",
  "invalidParams": [
    {
      "name": "email",
      "reason": "must be a well-formed email address"
    }
  ]
}
```

### 4.2 Example: Active Identity Conflict (`409`)
```json
{
  "type": "https://api.example.com/errors/duplicate-email",
  "title": "Email Conflict",
  "status": 409,
  "detail": "An active user with email 'john.doe@example.com' already exists.",
  "instance": "/api/v1/users"
}
```

### 4.3 Error Handling Matrix

| Error Category | Native Protocol Status / Code / Strategy | Trigger Condition | Native Error Payload / Handling |
|---|---|---|---|
| Bad Input / Validation | `400 Bad Request` | `@NotBlank`, `@Email`, `@Past`, `@Size`, `@Pattern` violations on DTO | RFC 7807 `type=.../validation-failed`, `title=Validation Failed`, `invalidParams[]` |
| Bad Input / Syntax | `400 Bad Request` | Unparseable JSON, invalid date, incompatible types | RFC 7807 `type=.../malformed-json`, `title=Malformed JSON Payload` |
| Conflict / Illegal State (self-parent) | `400 Bad Request` | `fatherId` or `motherId` equals the target user id | RFC 7807 `type=.../invalid-parent-relation`, `title=Invalid Parent Relationship` |
| Target Absence | `404 Not Found` | GET/PUT/DELETE target id missing **or** `is_deleted = true` | RFC 7807 `type=.../user-not-found`, `title=User Not Found` |
| Target Absence (parent) | `404 Not Found` | `fatherId` / `motherId` missing **or** soft-deleted | RFC 7807 `type=.../parent-not-found`, `title=Parent User Not Found` |
| Conflict / Illegal State (email) | `409 Conflict` | Another **active** user already has this email | RFC 7807 `type=.../duplicate-email`, `title=Email Conflict` |
| Conflict / Illegal State (phone) | `409 Conflict` | Another **active** user already has this phone | RFC 7807 `type=.../duplicate-phone`, `title=Phone Number Conflict` |

Soft-deleted email/phone reuse is **not** a conflict. Physical deletion and hard unique indexes on all rows (including deleted) are forbidden.
