# Feature Specification: user-service

## 1. Overview & Communication Pattern
- **Feature Identifier**: `user-service`
- **Target Scope**: Production-grade Kotlin / Quarkus 3.x microservice providing full CRUD operations, direct retrieval by ID and normalized email, active direct family graph retrieval, RFC 7807 Problem Details error handling, individual address deduplication, and database initialization via Flyway seeding 1,000 users across 20 distinct locations.
- **Communication Pattern**: Synchronous Request / Reply (REST API over HTTP/JSON).
- **Participants**:
  - API Client / Gateway (Caller / Initiator)
  - `user-service` Quarkus Application (Responder / Handler)
  - H2 Database TCP Server (State Store)

---

## 2. Interface & Interaction Contract
- **Target Identifier / Location**: Base path `/api/v1/users`
- **Interaction Semantics**: Synchronous Request / Response
- **Transport & Wire Format**: HTTP/1.1 / JSON (`application/json`, RFC 7807 `application/problem+json`)

### 2.1 Inbound / Outbound Schemas

#### Endpoints
1. `POST /api/v1/users`
   - **Status**: `201 Created`
   - **Header**: `Location: /api/v1/users/{id}`
   - **Input Body** (`CreateUserRequest`):
     ```json
     {
       "firstName": "John",
       "lastName": "Doe",
       "birthDate": "1990-05-15",
       "email": "john.doe@example.com",
       "phoneNumber": "+1234567890",
       "address": {
         "country": "Germany",
         "city": "Berlin",
         "street": "Alexanderplatz",
         "building": "1",
         "apartment": "10B",
         "postalCode": "10178"
       },
       "fatherId": 10,
       "motherId": 11
     }
     ```
   - **Output Body** (`UserResponse`): Complete persisted entity DTO loaded from database with generated `id` and normalized values.

2. `GET /api/v1/users/{id}`
   - **Status**: `200 OK`
   - **Output Body** (`UserResponse`):
     ```json
     {
       "id": 1,
       "firstName": "John",
       "lastName": "Doe",
       "birthDate": "1990-05-15",
       "email": "john.doe@example.com",
       "phoneNumber": "+1234567890",
       "address": {
         "id": 5,
         "country": "Germany",
         "city": "Berlin",
         "street": "Alexanderplatz",
         "building": "1",
         "apartment": "10B",
         "postalCode": "10178"
       },
       "fatherId": 10,
       "motherId": 11,
       "childrenIds": [25, 26]
     }
     ```

3. `GET /api/v1/users/by-email?email={email}`
   - **Status**: `200 OK`
   - **Output Body** (`UserResponse`): Active user matching lowercase normalized email.

4. `PUT /api/v1/users/{id}`
   - **Status**: `200 OK`
   - **Semantics**: Full-resource replacement. Missing or explicit `null` optional fields reset persisted columns to SQL `NULL`. Address is relinked or newly created; existing shared address records are never mutated.
   - **Input Body** (`UpdateUserRequest`): Same schema as `CreateUserRequest`.
   - **Output Body** (`UserResponse`): Updated user profile.

5. `DELETE /api/v1/users/{id}`
   - **Status**: `204 No Content`
   - **Semantics**: Soft delete (`is_deleted = true`). Record is retained in database; parental relationships pointing to this user remain intact for genealogy traversal.

6. `GET /api/v1/users/{id}/family`
   - **Status**: `200 OK`
   - **Output Body**: Top-level JSON array of `UserResponse` objects (`List<UserResponse>`), containing active user (if active), active direct parents, and active direct children. Excludes recursive/extended relatives.
   - If user exists and is active with no active family: `[ user ]`.
   - If user exists and is soft-deleted: returns only active direct family members `[ relative1, ... ]` (or `[]` if none).
   - If ID never existed in database: returns `404 Not Found`.

#### Serialization Invariants
- Optional response fields with no value (`phoneNumber`, `address`, `fatherId`, `motherId`, and optional address fields) MUST be omitted from JSON (`@JsonInclude(JsonInclude.Include.NON_NULL)`), never serialized as `null`.
- Collection fields (`childrenIds`) are always serialized as JSON arrays (`[]` when empty).

### 2.2 Side-Effects, State Mutations & Events
- **Persistence Changes**:
  - `User`: Inserted on `POST`, updated on `PUT`, marked `is_deleted = true` on `DELETE`.
  - `Address`: Reused if an identical record exists (case-insensitive, whitespace-collapsed match on all 6 components); otherwise a new `Address` row is inserted. Existing address rows are never modified.
- **Parental Links**: Soft deletion retains `father_id` and `mother_id` links.
- **External Integration / Events**: None in MVP scope.

---

## 3. Domain Model & Validation Matrix

### 3.1 Entity: `User`
| Field | Type | Required | Constraints & Invariants | Status / Traceability |
|---|---|---|---|---|
| `id` | `Long` | Generated | Primary Key, auto-increment | Confirmed |
| `firstName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `lastName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | Confirmed |
| `birthDate` | `LocalDate` | Yes | `@NotNull`, `@Past` | Confirmed |
| `email` | `String` | Yes | `@NotBlank`, `@Email`, normalized to lowercase before save. Unique among active users (`is_deleted = false`) | Confirmed |
| `phoneNumber` | `String` | No | Normalized to E.164 (`+` and 7-15 digits: `^\+[1-9]\d{6,14}$`). Unique among active users if present | Confirmed |
| `address` | `Address` | No | ManyToOne FK `address_id` | Confirmed |
| `father` | `User` | No | ManyToOne FK `father_id`. Must reference existing active user; `father_id != id`; cannot equal `mother_id`; no ancestry cycles | Confirmed |
| `mother` | `User` | No | ManyToOne FK `mother_id`. Must reference existing active user; `mother_id != id`; cannot equal `father_id`; no ancestry cycles | Confirmed |
| `isDeleted` | `Boolean` | Yes | Default `false`. Filtered out on all standard queries | Confirmed |
| `children` | `List<User>`| Derived | Derived query: `father_id = :id OR mother_id = :id` where `is_deleted = false` | Confirmed |

### 3.2 Entity: `Address`
| Field | Type | Required | Constraints & Invariants | Status / Traceability |
|---|---|---|---|---|
| `id` | `Long` | Generated | Primary Key, auto-increment | Confirmed |
| `country` | `String` | Yes | `@NotBlank`, `@Size(max=100)` | Confirmed |
| `city` | `String` | Yes | `@NotBlank`, `@Size(max=100)` | Confirmed |
| `street` | `String` | Yes | `@NotBlank`, `@Size(max=150)` | Confirmed |
| `building` | `String` | Yes | `@NotBlank`, `@Size(max=50)` | Confirmed |
| `apartment` | `String` | No | `@Size(max=50)` | Confirmed |
| `postalCode` | `String` | No | `@Size(max=20)` | Confirmed |

---

## 4. Failure Protocol & Error Dynamics (RFC 7807)

Content-Type: `application/problem+json`

| Error Category | HTTP Status | Trigger Condition | Problem Details Schema & Payload |
|---|:---:|---|---|
| Bad Input / Validation | `400 Bad Request` | Constraint violation, blank required fields, future birth date, invalid phone/email format | `type`: `"urn:problem:bad-request"`, `title`: `"Bad Request"`, `status`: 400, `detail`: `"Validation failed"`, `invalidParams`: `[{"name": "email", "reason": "must be a well-formed email address"}]` |
| Self / Cyclic Parent | `400 Bad Request` | User set as own parent, same person as father and mother, or ancestor cycle | `type`: `"urn:problem:bad-request"`, `title`: `"Bad Request"`, `status`: 400, `detail`: `"Parent assignment creates an invalid or cyclic relationship"` |
| Target Absence | `404 Not Found` | Requested user ID or email does not exist or is soft-deleted; referenced parent does not exist or is soft-deleted | `type`: `"urn:problem:not-found"`, `title`: `"Not Found"`, `status`: 404, `detail`: `"User with identifier ... not found"` |
| Uniqueness Conflict | `409 Conflict` | Normalized email or phone already used by an active user | `type`: `"urn:problem:conflict"`, `title`: `"Conflict"`, `status`: 409, `detail`: `"User with this normalized email or phone number already exists"` |

---

## 5. Infrastructure & Environment Manifest
*(SSOT for `environment-bootstrap`)*

### 5.1 Build Tool & Manifest Spec
- **Build Tool**: Gradle with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`).
- **Target Runtime**: Kotlin 2.0.0, JVM 21.
- **Plugins**:
  - `io.quarkus` version `3.15.1` (or latest stable Quarkus 3.x)
  - `org.jetbrains.kotlin.jvm` version `2.0.0`
  - `org.jetbrains.kotlin.plugin.allopen` version `2.0.0`
  - `org.jetbrains.kotlin.plugin.noarg` version `2.0.0`
- **Dependencies**:
  - `io.quarkus:quarkus-resteasy-reactive-jackson`
  - `io.quarkus:quarkus-hibernate-orm-panache-kotlin`
  - `io.quarkus:quarkus-hibernate-validator`
  - `io.quarkus:quarkus-flyway`
  - `io.quarkus:quarkus-jdbc-h2`
  - `com.fasterxml.jackson.module:jackson-module-kotlin`
  - Test scope: `io.quarkus:quarkus-junit5`, `io.rest-assured:rest-assured`, `io.mockk:mockk`
- **Wrapper**: Generate `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.8+).

### 5.2 Database Migration Spec
- **Migration Tool**: Flyway (managed automatically via `quarkus-flyway`).
- **Migration Files**:
  1. `src/main/resources/db/migration/V1__init_schema.sql`:
     - Creates tables `addresses` and `users`.
     - Creates foreign key constraints (`fk_users_address`, `fk_users_father`, `fk_users_mother`).
     - Creates partial unique indexes on active records:
       - `CREATE UNIQUE INDEX uq_users_active_email ON users(email) WHERE is_deleted = FALSE;`
       - `CREATE UNIQUE INDEX uq_users_active_phone ON users(phone_number) WHERE is_deleted = FALSE AND phone_number IS NOT NULL;`
  2. `src/main/resources/db/migration/V2__seed_users.sql`:
     - Seeds exactly 20 distinct addresses.
     - Seeds exactly 1,000 active users distributed across the 20 addresses (10 to 100+ cohabitants per location).
     - Enforces interconnected family trees with chronologically consistent parental ages (`birthDate(parent) < birthDate(child)`).

### 5.3 Runtime & Container Properties
- **Application Configuration**:
  - `src/main/resources/application.properties`:
    - Default local profile: in-memory H2 database or TCP client (`quarkus.datasource.jdbc.url=jdbc:h2:tcp://localhost:9092/mem:userdb;DB_CLOSE_DELAY=-1`).
    - `%docker` profile: connects to `jdbc:h2:tcp://h2-db:9092/mem:userdb;DB_CLOSE_DELAY=-1`.
    - `quarkus.flyway.migrate-at-start=true`.
    - `quarkus.hibernate-orm.database.generation=none`.
- **Dockerfile**: Multi-stage JVM build creating fast-jar / quarkus-app image based on `eclipse-temurin:21-jre-alpine`.
- **docker-compose.yml**:
  - `user-service`: depends on `h2-db`, port `8080:8080`, environment `QUARKUS_PROFILE=docker`.
  - `h2-db`: standalone H2 TCP server container, port `9092:9092`.

### 5.4 Directory Tree Specification
```
user-service/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── Dockerfile
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/userservice/
│   │   │       ├── domain/
│   │   │       │   ├── entity/
│   │   │       │   └── repository/
│   │   │       ├── service/
│   │   │       ├── api/
│   │   │       │   ├── resource/
│   │   │       │   ├── dto/
│   │   │       │   └── exception/
│   │   │       └── util/
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql
│   │           └── V2__seed_users.sql
│   └── test/
│       ├── kotlin/
│       │   └── com/example/userservice/
│       └── resources/
│           └── application.properties
```
