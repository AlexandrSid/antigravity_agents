# Feature Specification: User Service (Core CRUD, Search & Demographic Graph)

## 1. Overview & Communication Pattern
- **Feature Identifier**: `user-service`
- **Target Scope**: Implementation of complete CRUD operations, fuzzy multi-criteria search with pagination, soft-delete lifecycle management, normalized address sharing, and Flyway database migration seeding 1,000 interconnected users across 20 distinct physical locations.
- **Communication Pattern**: Synchronous Request/Reply via RESTful HTTP API.
- **Participants**:
  - API Consumers / External Clients
  - `UserController` (REST boundary)
  - `UserService` (Domain orchestrator & business invariant enforcement)
  - `UserRepository` & `AddressRepository` (Spring Data JPA abstraction)
  - Data Store: H2 Database in standalone TCP Server mode (Docker container `h2-db`)

---

## 2. Interface & Interaction Contract
- **Base Endpoint**: `/api/v1/users`
- **Interaction Semantics**: Synchronous Request/Response
- **Transport & Wire Format**: HTTP/1.1, `Content-Type: application/json`, `Accept: application/problem+json, application/json`

### 2.1 Endpoints & Payload Schemas

#### 1. Create User
- **Method & Path**: `POST /api/v1/users`
- **Success Status**: `201 Created`
- **Headers**: `Location: /api/v1/users/{id}`
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
*(Note: `phoneNumber`, `address`, `fatherId`, `motherId` are optional. Within `address`, `apartment` and `postalCode` are optional).*

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
- **Response Body (`200 OK`)**:
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
  "childrenIds": [1040, 1045]
}
```
- **Error Conditions**: If user does not exist or has `isDeleted = true`, returns RFC 7807 `404 Not Found`.

---

#### 3. Update User (PUT Full-Replacement Semantics)
- **Method & Path**: `PUT /api/v1/users/{id}`
- **Success Status**: `200 OK`
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
- **PUT Null-Handling Semantics**:
  - `PUT` enforces full-resource replacement semantics.
  - Passing `null` for optional attributes (`phoneNumber`, `fatherId`, `motherId`, `address`) **explicitly resets** those fields to `NULL` in the database.
  - Passing `null` for `address.apartment` or `address.postalCode` clears those values on the linked address record.
  - Address reassignment is individual: relinks the user to an existing matching address or inserts a new normalized address row, preserving existing address records shared by other cohabitants.
- **Response Body (`200 OK`)**: Updated User representation matching GET schema.

---

#### 4. Soft Delete User
- **Method & Path**: `DELETE /api/v1/users/{id}`
- **Success Status**: `204 No Content`
- **Behavior**: Sets `is_deleted = true`. Soft-deleted users are immediately excluded from standard lookups and searches.

---

#### 5. Multi-Criteria Dynamic Search
- **Method & Path**: `GET /api/v1/users`
- **Success Status**: `200 OK`
- **Query Parameters**:
  - `name` (String, optional): Case-insensitive substring match (`LIKE %value%`) against `firstName` OR `lastName`.
  - `email` (String, optional): Case-insensitive substring match against `email`.
  - `phone` (String, optional): Case-insensitive substring match against `phoneNumber`.
  - `familyMemberId` (Long, optional): Matches any active user for whom the given ID is their father, mother, or child.
    - **Non-existent / Inactive Member Semantics**: If `familyMemberId` does not correspond to an active user or has no relatives, the search returns `200 OK` with an empty list (`content: []`, `totalElements: 0`).
  - `country` (String, optional): Matches address country (`LIKE %value%`).
  - `city` (String, optional): Matches address city (`LIKE %value%`).
  - `street` (String, optional): Matches address street (`LIKE %value%`).
  - `building` (String, optional): Matches address building (`LIKE %value%`).
  - `postalCode` (String, optional): Matches address postal code (`LIKE %value%`).
  - `page` (Integer, default: `0`): Zero-based page index.
  - `size` (Integer, default: `10`): Number of items per page.
  - `sort` (String, default: `id,asc`): Sort field and direction.
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
- **Persistence Changes & Partial Unique Indexing**:
  - **`users` Table Mutations**:
    - `INSERT`: Generates new auto-increment ID, stores `first_name`, `last_name`, `birth_date`, `email`, `phone_number`, `father_id`, `mother_id`, `address_id`, with default `is_deleted = false`.
    - `UPDATE`: Overwrites demographic fields. Resets nullable fields to `NULL` if provided as `null`.
    - `SOFT DELETE`: Updates row state to `is_deleted = true`. Never executes physical SQL `DELETE`.
  - **Soft Delete Partial Unique Indexes (Flyway `V1__init_schema.sql`)**:
    - To prevent soft-deleted rows from blocking reuse of emails or phone numbers, Flyway MUST define partial unique indexes restricted to active rows:
      ```sql
      CREATE UNIQUE INDEX idx_users_email_active ON users(email) WHERE is_deleted = false;
      CREATE UNIQUE INDEX idx_users_phone_active ON users(phone_number) WHERE is_deleted = false AND phone_number IS NOT NULL;
      ```
  - **`addresses` Table Mutations**:
    - De-duplication check: If country, city, street, building, and apartment match an existing row, the existing `id` is linked without mutating the row.
    - If no exact match exists, inserts a new row with generated primary key.
  - **Database Migration & Seeding**:
    - Flyway executes `V1__init_schema.sql` (creates `addresses` and `users` tables, partial indexes, foreign keys).
    - Flyway executes `V2__seed_1000_users.sql` on initial migration, populating exactly 20 distinct `addresses` and 1,000 `users` with interconnected parental links, satisfying chronological sanity (`birthDate(parent) < birthDate(child)`).
- **Emitted Events / Messages**: None in `[TARGET]` scope (pure synchronous REST API).
- **External Integration Side-Effects**: None in `[TARGET]` scope.

---

## 3. Domain Model & Validation Matrix

### 3.1 Entity Schemas & Validation Rules

| Entity | Field / Property | Type | Required | Constraints & Validation Rules | Status / Traceability |
|---|---|---|---|---|---|
| `User` | `id` | `Long` | No (Generated) | Primary Key, Auto-increment | Confirmed |
| `User` | `firstName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `User` | `lastName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `User` | `birthDate` | `LocalDate` | Yes | `@NotNull`, `@Past` | Confirmed |
| `User` | `email` | `String` | Yes | `@NotBlank`, `@Email`, `@Size(max=255)`. Filtered unique index: `idx_users_email_active` (`WHERE is_deleted = false`) | `[AI-ASSUMPTION: Email is unique primary business key \| Attention Needed for Implementer]` |
| `User` | `phoneNumber` | `String` | No | `@Pattern(regexp = "^\\+?[0-9. ()-]{7,25}$")`. Filtered unique index: `idx_users_phone_active` (`WHERE is_deleted = false AND phone_number IS NOT NULL`) | Confirmed |
| `User` | `isDeleted` | `Boolean` | Yes | Default: `false`. Soft-delete flag | Confirmed |
| `User` | `address` | `Address` | No | `@ManyToOne(fetch = FetchType.LAZY)`, Foreign Key: `address_id` | Confirmed |
| `User` | `father` | `User` | No | `@ManyToOne(fetch = FetchType.LAZY)`, Foreign Key: `father_id` | Confirmed |
| `User` | `mother` | `User` | No | `@ManyToOne(fetch = FetchType.LAZY)`, Foreign Key: `mother_id` | Confirmed |
| `User` | `children` | `List<User>` | No (Derived) | Computed via query `WHERE (father_id = :id OR mother_id = :id) AND is_deleted = false` | `[AI-ASSUMPTION: Children computed on demand to avoid recursion/cycle serialization \| Attention Needed for Implementer]` |
| `Address` | `id` | `Long` | No (Generated) | Primary Key, Auto-increment | Confirmed |
| `Address` | `country` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `Address` | `city` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `Address` | `street` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=150)` | Confirmed |
| `Address` | `building` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=50)` | Confirmed |
| `Address` | `apartment` | `String` | No | `@Size(max=50)`. Resets to `null` on PUT if provided as `null` | Confirmed |
| `Address` | `postalCode` | `String` | No | `@Size(max=20)`. Resets to `null` on PUT if provided as `null` | Confirmed |

---

## 4. Failure Protocol & Error Dynamics (Strict RFC 7807 Compliance)

All error responses strictly adhere to **RFC 7807 (Problem Details for HTTP APIs)**, using `Content-Type: application/problem+json`.

### 4.1 RFC 7807 Schema Definition
- **`type`** (URI reference): A URI identifier that categorizes the problem type.
- **`title`** (String): Short, human-readable summary of the problem type.
- **`status`** (Integer): The HTTP status code generated by the origin server.
- **`detail`** (String): Human-readable explanation specific to this occurrence of the problem.
- **`instance`** (URI reference): A URI reference that identifies the specific resource or request endpoint.
- **`invalidParams`** (Array, optional): Extended attribute for validation errors listing field-level infractions.

#### Example RFC 7807 Payload: Validation Failure (`400 Bad Request`)
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

#### Example RFC 7807 Payload: Resource Conflict (`409 Conflict`)
```json
{
  "type": "https://api.example.com/errors/duplicate-email",
  "title": "Email Conflict",
  "status": 409,
  "detail": "An active user with email 'john.doe@example.com' already exists.",
  "instance": "/api/v1/users"
}
```

### 4.2 Error Handling Matrix (RFC 7807)

| Error Category | HTTP Status | Problem `type` | Problem `title` | Trigger Condition | Detail / Example |
|---|---|---|---|---|---|
| Validation Error | `400 Bad Request` | `https://api.example.com/errors/validation-failed` | `Validation Failed` | Violations of `@NotBlank`, `@Email`, `@Past`, or `@Size` on request body | Includes `invalidParams` array with field-level constraint messages |
| Malformed Syntax | `400 Bad Request` | `https://api.example.com/errors/malformed-json` | `Malformed JSON Payload` | Unparseable JSON, invalid date format, or incompatible types | `"Failed to parse request payload: invalid JSON syntax or format."` |
| Self-Parenting Conflict | `400 Bad Request` | `https://api.example.com/errors/invalid-parent-relation` | `Invalid Parent Relationship` | User ID equals `fatherId` or `motherId` (`father_id == id` or `mother_id == id`) | `"User cannot be designated as their own father or mother."` |
| Target Absence | `404 Not Found` | `https://api.example.com/errors/user-not-found` | `User Not Found` | Specified user `id` does not exist or has `is_deleted = true` | `"User with ID 9999 does not exist or has been deleted."` |
| Parent Absence | `404 Not Found` | `https://api.example.com/errors/parent-not-found` | `Parent User Not Found` | Referenced `fatherId` or `motherId` does not exist or has `is_deleted = true` | `"Referenced parent user with ID 8888 does not exist or has been deleted."` |
| Email Uniqueness Collision | `409 Conflict` | `https://api.example.com/errors/duplicate-email` | `Email Conflict` | An active user (`is_deleted = false`) already exists with the same email | `"An active user with email '...' already exists."` |
| Phone Uniqueness Collision | `409 Conflict` | `https://api.example.com/errors/duplicate-phone` | `Phone Number Conflict` | An active user (`is_deleted = false`) already exists with the same phone | `"An active user with phone number '...' already exists."` |

---

## 5. Architectural Non-Functional Requirements & Environment
- **Multi-Container Topology (`docker-compose.yml`)**:
  - Service `user-service`: Builds from local `Dockerfile` (multi-stage build on Java 21 JDK -> JRE), runs on port `8080`.
  - Service `h2-db`: Standalone H2 TCP server container running on port `9092` with persistent Docker volume mounted for data.
- **Flyway Seed Invariant**:
  - `V1__init_schema.sql`: Table definitions with foreign keys and partial unique indexes (`WHERE is_deleted = false`).
  - `V2__seed_1000_users.sql`: Exactly 20 addresses and 1,000 users. Generational order ensures all parents are inserted before children and parent birth dates precede children birth dates (`[AI-ASSUMPTION: Seed script chronological sanity | Attention Needed for Implementer]`).
- **Layering & Isolation**:
  - `src/main/` strictly separated into Controller, Service, and Repository layers.
  - DTOs used exclusively at the Controller layer (`UserCreateRequest`, `UserUpdateRequest`, `UserResponse`, `AddressDto`). JPA Entities are never directly exposed in API responses.
