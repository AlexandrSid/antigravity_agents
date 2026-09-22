# Product Requirements Document (PRD)

## 1. Target Iteration Goals
- **Project / Feature Name**: User Service (Full CRUD, Search & Demographic Graph)
- **Target Iteration**: MVP / Sprint 1
- **Core Objective**: Deliver a production-grade, containerized Spring Boot REST API for managing users with demographic data, normalized shared addresses, biological family ties, soft deletion, fuzzy multi-criteria search, and an automated Flyway migration seeding 1,000 users across 20 distinct locations.
- **Key Success Metrics**:
  - Full CRUD operations implemented: Create (`POST`), Read (`GET`), Update (`PUT`), and Soft Delete (`DELETE`).
  - Search endpoint supporting fuzzy (`LIKE %value%`) multi-criteria filtering with default pagination (`size=10`).
  - Flyway migration script successfully initializing schema and 1,000 interconnected users with consistent parental age relationships.
  - Multi-container Docker Compose deployment (Spring Boot app + separate H2 TCP Server container).
  - Clean 3-tier architecture: `Controller` -> `Service` -> `Repository`.

### 1.1 Target Tech Stack & Environment Constraints
*(Discovered during user interview - serves as Source of Truth for downstream agents)*
- **Primary Runtime / Language**: Java 21
- **Build Tool**: Gradle with Kotlin DSL (`build.gradle.kts`)
- **Frameworks & Libraries**:
  - Spring Boot 3.3.x (Spring Web, Spring Data JPA, Jakarta Validation)
  - Flyway (database schema management and seed data migrations)
  - Lombok `[AI-ASSUMPTION: Standard boilerplate reduction]`
- **Data Persistence / Storage**: H2 Database running in TCP Server mode as an independent Docker container
- **Communication Protocol**: REST API (HTTP/JSON)
- **Deployment & Containerization**: Docker Compose (`docker-compose.yml`) containing:
  - `user-service`: Spring Boot application container
  - `h2-db`: Standalone H2 TCP server container
- **Cross-Cutting Architectural Constraints**:
  - Strict 3-tier architecture: Controller (`UserController`), Service (`UserService`), Repository (`UserRepository`, `AddressRepository`).
  - Configuration strictly separated into individual YAML files (`application.yml`, `application-docker.yml`).
  - Soft-delete strategy: users are never removed from disk; state is marked via `is_deleted = true`.

---

## 2. Domain Entities & Validation Matrix

### 2.1 Entity: `User`
Represents an individual with demographic data, address reference, biological family ties, and soft-delete state.

| Entity | Field | Type | Required | Constraints | Scope | Status / Assumption |
|---|---|---|---|---|---|---|
| `User` | `id` | `Long` | No (Generated) | Auto-increment primary key | `[TARGET]` | Confirmed |
| `User` | `firstName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | `[TARGET]` | Confirmed |
| `User` | `lastName` | `String` | Yes | `@NotBlank`, `@Size(min=1, max=100)` | `[TARGET]` | Confirmed |
| `User` | `birthDate` | `LocalDate` | Yes | `@NotNull`, `@Past` | `[TARGET]` | Confirmed |
| `User` | `email` | `String` | Yes | `@NotBlank`, `@Email`, unique index | `[TARGET]` | Confirmed (Strictly Unique) |
| `User` | `phoneNumber` | `String` | No | Unique index if present, `@Pattern` standard phone | `[TARGET]` | Confirmed (Strictly Unique if present) |
| `User` | `address` | `Address` | No | Foreign Key `address_id` (`ManyToOne`) | `[TARGET]` | Confirmed (Shared across users) |
| `User` | `father` | `User` | No | Foreign Key `father_id` (`ManyToOne`) | `[TARGET]` | Confirmed (References existing User) |
| `User` | `mother` | `User` | No | Foreign Key `mother_id` (`ManyToOne`) | `[TARGET]` | Confirmed (References existing User) |
| `User` | `isDeleted` | `Boolean` | Yes | Default: `false`. Soft-delete flag | `[TARGET]` | Confirmed (Soft delete) |
| `User` | `children` | `List<User>` | No (Derived) | Read-only derived query (`father_id = :id OR mother_id = :id`) | `[TARGET]` | Confirmed |

### 2.2 Entity: `Address`
Represents a physical living location shared across cohabitants / family members.

| Entity | Field | Type | Required | Constraints | Scope | Status / Assumption |
|---|---|---|---|---|---|---|
| `Address` | `id` | `Long` | No (Generated) | Auto-increment primary key | `[TARGET]` | Confirmed |
| `Address` | `country` | `String` | Yes | `@NotBlank`, `@Size(max=100)` | `[TARGET]` | Confirmed |
| `Address` | `city` | `String` | Yes | `@NotBlank`, `@Size(max=100)` | `[TARGET]` | Confirmed |
| `Address` | `street` | `String` | Yes | `@NotBlank`, `@Size(max=150)` | `[TARGET]` | Confirmed |
| `Address` | `building` | `String` | Yes | `@NotBlank`, `@Size(max=50)` | `[TARGET]` | Confirmed |
| `Address` | `apartment` | `String` | No | `@Size(max=50)` | `[TARGET]` | Confirmed (Optional) |
| `Address` | `postalCode` | `String` | No | `@Size(max=20)` | `[TARGET]` | Confirmed (Optional) |

---

## 3. Business Rules & State Transitions (Scope: [TARGET])

### 3.1 Domain Invariants & Business Rules
- **Rule 1 (Email & Phone Uniqueness)**: 
  - `email` must be globally unique among active users. Duplicate returns `409 Conflict`.
  - `phoneNumber` (if provided) must be globally unique among active users. Duplicate returns `409 Conflict`.
- **Rule 2 (Soft Delete Semantics)**: 
  - Executing `DELETE /api/v1/users/{id}` sets `is_deleted = true`.
  - All standard retrieval and search queries filter out soft-deleted users (`WHERE is_deleted = false`).
- **Rule 3 (Address Reusability & Individual Mutation)**:
  - When creating a user with address fields, if an identical address already exists (same country, city, street, building, apartment), link to existing `Address`.
  - When updating a user's address, the change is individual: the user is relinked to an existing matching address or a new `Address` is created, leaving other family members at their original address.
- **Rule 4 (Parental References)**:
  - `father_id` and `mother_id` must reference valid existing non-deleted users.
  - A user cannot be their own parent (`father_id != id` and `mother_id != id`).
- **Rule 5 (Fuzzy Search & Pagination)**:
  - Search matches text attributes (name, email, phone, address fields) using case-insensitive partial match (`LIKE %value%`).
  - Default pagination limit: `page = 0, size = 10`.

### 3.2 REST API Surface (Scope: [TARGET])
1. **Create User**:
   - `POST /api/v1/users`: Create user with demographic data, optional address, optional parent IDs. Returns `201 Created`.
2. **Get User by ID**:
   - `GET /api/v1/users/{id}`: Returns user profile, address, parents, and direct children. Returns `200 OK` or `404 Not Found`.
3. **Update User**:
   - `PUT /api/v1/users/{id}`: Update individual user details (demographics, phone, individual address, parents). Returns `200 OK`.
4. **Soft Delete User**:
   - `DELETE /api/v1/users/{id}`: Marks user as `is_deleted = true`. Returns `204 No Content` or `200 OK`.
5. **Multi-Criteria Dynamic Search**:
   - `GET /api/v1/users`: Dynamic filtering with query parameters:
     - `name`: Matches first name or last name (`LIKE %value%`)
     - `email`: Fuzzy match on email (`LIKE %value%`)
     - `phone`: Fuzzy match on phone (`LIKE %value%`)
     - `familyMemberId`: Finds users where the specified ID is father, mother, or child
     - `country`, `city`, `street`, `building`, `postalCode`: Fuzzy match on address fields
     - `page` (default 0), `size` (default 10), `sort` (default `id,asc`)

### 3.3 Database Seeding Specification (1,000 Users, 20 Locations)
- **Dataset Scale**: Exactly 1,000 active users distributed across 20 distinct addresses (uneven distribution: 10 to 100+ cohabitants per location).
- **Interconnected Family Graph Invariant**:
  - In the seed dataset, parental relationships MUST be chronologically consistent: parents are strictly older than their children (`birthDate(parent) < birthDate(child)`).
  - Multi-generational trees (grandparents -> parents -> children) living at shared addresses.
- **Delivery Mechanism**:
  - Flyway migration script (e.g., `V2__seed_1000_users.sql` or automated Flyway Java migration) executed automatically on startup.

---

## 4. Out of Scope / Backlog
Features identified during discovery that are strictly deferred from the current target iteration:
- **[BACKLOG] Runtime Parental Age Validation on API Input**: Validating that parent is older than child during `POST/PUT /api/v1/users` is deferred (enforced only in initial seed dataset).
- **[BACKLOG] Bulk Family Address Update**: Dedicated endpoint to atomically relocate an entire family group to a new address.
- **[BACKLOG] Complex Genealogical Graph Queries**: Recursive multi-degree ancestor/descendant graph traversal endpoints.
- **[BACKLOG] Hard Deletion & Cascade Purge**: Physical deletion of records and unlinking cascades from database.
- **[BACKLOG] Authentication & Authorization**: Spring Security, JWT, OAuth2 tokens.
- **[BACKLOG] External Production Database Migration**: Migration from H2 to PostgreSQL/MySQL.

---

## 5. DEFINITION OF DONE
The Planner's discovery phase is complete ONLY when:
1. **PRD Persisted**: All requirements are documented in `docs/PRD.md`.
2. **Strict Scope Partitioning**: All requirements are partitioned into `Scope: [TARGET]` or `Scope: [BACKLOG]`.
3. **Zero Ambiguity in Target**: `Scope: [TARGET]` contains zero unclarified business logic, zero mocked domain rules, and zero blind spots.
