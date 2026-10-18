# Feature Summary: user-service

> [!WARNING]
> **AI AGENT USAGE RESTRICTION**:
> This document is a human-readable summary compiled solely for human operators, reviewers, and developers.
> **AI agents MUST NOT use this file as a Single Source of Truth (SSOT) or technical contract reference.**
> Authoritative sources of truth remain:
> - Architecture & Schemas: [`docs/features/user-service/SPEC.md`](file:///c:/Users/Aleksandr/AI%20projects/antigravity_agents/docs/features/user-service/SPEC.md)
> - Product Requirements: [`docs/PRD.md`](file:///c:/Users/Aleksandr/AI%20projects/antigravity_agents/docs/PRD.md)
> - Environment & Toolchain: [`docs/PROJECT_ENV.md`](file:///c:/Users/Aleksandr/AI%20projects/antigravity_agents/docs/PROJECT_ENV.md)

---

## 1. Executive Summary & Supported Scope
The `user-service` feature delivers a production-grade Kotlin 2.0 / Quarkus 3.x microservice providing demographic user profile management, biological family relationship tracking, address deduplication, and automated database seeding on H2.

### 1.1 Core Implemented Capabilities
- **Full CRUD REST API**:
  - `POST /api/v1/users`: Create user with demographic validation, address resolution, and parent links (`201 Created` with `Location` header).
  - `GET /api/v1/users/{id}`: Direct read of active user profile (`200 OK`).
  - `GET /api/v1/users/by-email?email={email}`: Direct read by lowercase normalized email (`200 OK`).
  - `PUT /api/v1/users/{id}`: Full resource replacement with individual address mutation (`200 OK`).
  - `DELETE /api/v1/users/{id}`: Soft delete marking `is_deleted = true` without breaking children references (`204 No Content`).
  - `GET /api/v1/users/{id}/family`: Retrieve active direct family members (self, parents, children) as a top-level JSON array (`200 OK`).
- **Domain Invariants**:
  - Normalized credentials uniqueness among active records (lowercase email, E.164 phone format).
  - Parentage rules: `father_id != id`, `mother_id != id`, `father_id != mother_id`, acyclic ancestry graph.
  - Case-insensitive, whitespace-collapsed address matching and deduplication.
  - Soft-delete semantics: active-only queries, soft-deleted users omitted from family endpoints.
- **RFC 7807 Problem Details**:
  - Unified error responses (`application/problem+json`) with standard fields: `type`, `title`, `status`, `detail`, `instance`, and `invalidParams`.
- **Flyway Database Initialization**:
  - `V1__init_schema.sql`: DDL for `addresses` and `users` tables, foreign keys, and indexes.
  - `V2__seed_users.sql`: Exactly 20 distinct physical addresses and 1,000 users across 3 generations, ensuring parents are chronologically older than their children (`birthDate(parent) < birthDate(child)`).
- **Multi-Container Architecture**:
  - JVM multi-stage `Dockerfile`.
  - `docker-compose.yml` orchestrating `user-service` and standalone `h2-db` TCP server.

---

## 2. API Contract Cheat Sheet

| HTTP Method | Path | Request Body | Success Status | Response Body | Error Codes |
|---|---|---|:---:|---|:---:|
| `POST` | `/api/v1/users` | `CreateUserRequest` | `201 Created` | `UserResponse` (`Location` header set) | `400`, `404`, `409` |
| `GET` | `/api/v1/users/{id}` | — | `200 OK` | `UserResponse` | `404` |
| `GET` | `/api/v1/users/by-email?email=...` | — | `200 OK` | `UserResponse` | `404` |
| `PUT` | `/api/v1/users/{id}` | `UpdateUserRequest` | `200 OK` | `UserResponse` | `400`, `404`, `409` |
| `DELETE` | `/api/v1/users/{id}` | — | `204 No Content` | *Empty* | `404` |
| `GET` | `/api/v1/users/{id}/family` | — | `200 OK` | `List<UserResponse>` | `404` |

---

## 3. Environment & Execution Commands

Sourced from [`docs/PROJECT_ENV.md`](file:///c:/Users/Aleksandr/AI%20projects/antigravity_agents/docs/PROJECT_ENV.md):
- **Compile / Build Check**: `./gradlew compileKotlin`
- **Unit & Slice Tests**: `./gradlew test`
- **Integration & E2E Tests**: `./gradlew test --tests "com.example.userservice.integration.*"`
- **Run Single Test**: `./gradlew test --tests "{test_class}"`

---

## 4. Verification Evidence & Quality Gates Status

| Step | Quality Gate | Status | Evidence / Notes |
|:---:|:---:|:---:|---|
| **04** | `COMPILE_CHECK` | **PASSED** | `./gradlew compileKotlin` completed with 0 errors. |
| **05** | `UNIT_RED_PHASE` | **PASSED** | Red Phase unit tests compiled and validated on skeleton stubs. |
| **06** | `UNIT_GREEN_PHASE` | **PASSED** | `./gradlew test` passed cleanly with 0 failures across all unit test classes. |
| **08** | `INTEGRATION_GREEN_PHASE`| **PASSED** | `./gradlew test` executed 100% green against real H2 database with Flyway migrations. |

---

## 5. Deferred Work (Out of Scope / Backlog)
As specified in PRD §4:
- Runtime parental age validation on API input (deferred; validated in seed dataset).
- Bulk family relocation endpoint.
- Recursive multi-degree ancestor/descendant graph queries.
- Hard delete & cascade purge.
- Authentication & Authorization (OIDC/JWT).
