# Feature Specification: User Service — CRUD, Direct Lookup & Family Graph

Binding sources: `docs/PRD.md` (`Scope: [TARGET]` only) and `.ai/guidelines/engineering-standards.md`. All `[BACKLOG]` capabilities are excluded.

## 1. Overview & Communication Pattern
- **Feature Identifier**: `user-service`
- **Target Scope**: User CRUD, lookup by ID or normalized email, direct-family retrieval, normalized shared addresses, soft deletion, and deterministic Flyway initialization with 1,000 users across 20 addresses.
- **Communication Pattern**: Synchronous request/reply.
- **Transport & Wire Format**: REST over HTTP; JSON success bodies and RFC 7807 Problem Details error bodies.
- **Participants**:
  - API client
  - `UserController` — HTTP boundary and DTO validation
  - `UserService` — normalization, invariants, cycle detection, soft delete, address resolution
  - `UserRepository` / `AddressRepository` — persistence boundary
  - H2 database
- **Layering**: Controller → Service → Repository. Controllers never access repositories directly.
- **Entity Boundary**: JPA entities are never serialized. All HTTP input/output uses DTOs.

---

## 2. Interface & Interaction Contract

### 2.1 Shared DTO Schemas

#### `AddressDto`
| Field | Type | Direction | Required | Contract |
|---|---|---|---|---|
| `id` | `Long` | Response only | No | Generated address ID; omitted from request |
| `country` | `String` | Request/Response | Yes | Not blank; max 100 |
| `city` | `String` | Request/Response | Yes | Not blank; max 100 |
| `street` | `String` | Request/Response | Yes | Not blank; max 150 |
| `building` | `String` | Request/Response | Yes | Not blank; max 50 |
| `apartment` | `String` | Request/Response | No | Max 50; absent/null means no apartment |
| `postalCode` | `String` | Request/Response | No | Max 20; absent/null means no postal code |

Every textual component is trimmed and internal whitespace sequences are collapsed to one ASCII space before comparison and persistence. Address identity comparison is case-insensitive across all six components. Any component difference, including `postalCode`, defines a different address.

#### `UserCreateRequest`
| Field | Type | Required | Contract |
|---|---|---|---|
| `firstName` | `String` | Yes | Not blank; 1–100 |
| `lastName` | `String` | Yes | Not blank; 1–100 |
| `birthDate` | `LocalDate` | Yes | Past date |
| `email` | `String` | Yes | Not blank; syntactically valid email |
| `phoneNumber` | `String` | No | Normalizable to E.164 `^\+[1-9]\d{6,14}$` |
| `address` | `AddressDto` | No | Nested fields validated when present |
| `fatherId` | `Long` | No | Existing active user |
| `motherId` | `Long` | No | Existing active user; differs from `fatherId` |

#### `UserUpdateRequest`
Same fields and validation as `UserCreateRequest`. `PUT` is full replacement:
- required fields must be present and valid;
- omitted or explicit-null optional fields (`phoneNumber`, `address`, `fatherId`, `motherId`) reset persistence columns to SQL `NULL`;
- address replacement always resolves by normalized match-or-insert and never mutates an existing address row.

#### `UserResponse`
| Field | Type | Required in JSON | Contract |
|---|---|---|---|
| `id` | `Long` | Yes | Generated user ID |
| `firstName` | `String` | Yes | Persisted value |
| `lastName` | `String` | Yes | Persisted value |
| `birthDate` | `LocalDate` | Yes | ISO `YYYY-MM-DD` |
| `email` | `String` | Yes | Persisted lowercase value |
| `phoneNumber` | `String` | No | Persisted E.164 value; omitted when null |
| `address` | `AddressDto` | No | Omitted when null |
| `fatherId` | `Long` | No | Included only when referenced father is active |
| `motherId` | `Long` | No | Included only when referenced mother is active |
| `childrenIds` | `List<Long>` | Yes | Active direct children only; empty array when none |

All null optional response fields are omitted. Collection fields are always emitted as arrays.

### 2.2 Endpoint Contracts

#### A. Create User
- **Method / Path**: `POST /api/v1/users`
- **Input**: `UserCreateRequest`
- **Success**: `201 Created`
- **Header**: `Location: /api/v1/users/{id}`
- **Body**: persisted `UserResponse` reloaded after save, including generated `id` and normalized values.
- **Behavior**:
  1. Normalize email to lowercase.
  2. Remove spaces, parentheses, and separators from phone; validate normalized E.164.
  3. Reject active normalized email/phone collisions.
  4. Resolve active parents.
  5. Reject identical father/mother IDs.
  6. Resolve address by normalized case-insensitive six-field identity; reuse exact match or insert a new row.

#### B. Get User by ID
- **Method / Path**: `GET /api/v1/users/{id}`
- **Success**: `200 OK`, `UserResponse`
- **Missing or soft-deleted target**: `404 Not Found`

#### C. Replace User
- **Method / Path**: `PUT /api/v1/users/{id}`
- **Input**: `UserUpdateRequest`
- **Success**: `200 OK`, persisted replacement `UserResponse`
- **Missing or soft-deleted target**: `404 Not Found`
- **Behavior**:
  1. Apply full replacement and normalization.
  2. Enforce active uniqueness excluding the current user.
  3. Resolve active parents; reject same-parent, self-parent, direct cycle, or indirect ancestry cycle.
  4. Null optional request values unlink/reset only this user.
  5. Resolve provided address by match-or-insert; never update existing address rows.

#### D. Soft Delete User
- **Method / Path**: `DELETE /api/v1/users/{id}`
- **Success**: `204 No Content`, empty body
- **Missing or already soft-deleted target**: `404 Not Found`
- **Behavior**: set `is_deleted = true`; never issue physical `DELETE`; preserve incoming family foreign keys in other users.

#### E. Get User by Email
- **Method / Path**: `GET /api/v1/users/by-email?email={email}`
- **Success**: `200 OK`, `UserResponse`
- **Behavior**: lowercase the supplied email and perform exact active-user lookup.
- **Missing or soft-deleted target**: `404 Not Found`

#### F. Get Direct Family
- **Method / Path**: `GET /api/v1/users/{id}/family`
- **Success**: `200 OK`, top-level JSON array of `UserResponse`; order is unspecified.
- **Membership**:
  - active anchor user, when the anchor is active;
  - active direct father and mother;
  - active direct children;
  - no recursive ancestors/descendants.
- **Anchor behavior**:
  - active anchor without active relatives → array containing only the anchor;
  - soft-deleted anchor with active relatives → active relatives only;
  - soft-deleted anchor without active relatives → empty array;
  - no row ever existed for ID → `404 Not Found`.

### 2.3 Side-Effects, State Mutations & Events
- **`users` mutations**:
  - POST inserts one active row.
  - PUT updates only the target row and its foreign-key links.
  - DELETE updates only `is_deleted`; no physical delete and no family-link cleanup.
- **`addresses` mutations**:
  - Exact normalized six-field match reuses an existing row.
  - No match inserts a new row.
  - User operations never update or delete an existing address row.
- **Uniqueness**: normalized email and normalized non-null phone are unique among active rows only. Values belonging only to soft-deleted rows may be reused.
- **Events/messages**: none.
- **External calls**: none.

---

## 3. Domain Model & Validation Matrix

| Type | Field | Persistence / Validation Contract |
|---|---|---|
| `User` | `id` | `BIGINT` identity PK |
| `User` | `firstName` | `VARCHAR(100) NOT NULL` |
| `User` | `lastName` | `VARCHAR(100) NOT NULL` |
| `User` | `birthDate` | `DATE NOT NULL`; API requires past |
| `User` | `email` | `VARCHAR(255) NOT NULL`; lowercase before persistence; active-only unique |
| `User` | `phoneNumber` | `VARCHAR(16)` nullable; E.164; active-only unique |
| `User` | `address` | Nullable many-to-one FK |
| `User` | `father` / `mother` | Nullable self-referencing many-to-one FKs; active on assignment |
| `User` | `isDeleted` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `Address` | `id` | `BIGINT` identity PK |
| `Address` | `country` | `VARCHAR(100) NOT NULL` |
| `Address` | `city` | `VARCHAR(100) NOT NULL` |
| `Address` | `street` | `VARCHAR(150) NOT NULL` |
| `Address` | `building` | `VARCHAR(50) NOT NULL` |
| `Address` | `apartment` | `VARCHAR(50)` nullable |
| `Address` | `postalCode` | `VARCHAR(20)` nullable |

### 3.1 Parent Invariants
- Parent references must resolve to active users when assigned.
- Target cannot reference itself.
- `fatherId` and `motherId` cannot be equal.
- An update must traverse parent links and reject any assignment that makes the target its own direct or indirect ancestor/descendant.
- Runtime parent-age validation is excluded because it is `[BACKLOG]`.

### 3.2 Propagated Assumptions
- Lombok is permitted for boilerplate reduction: `[AI-ASSUMPTION: Standard boilerplate reduction | Attention Needed for Implementer]`.
- Spring Boot `3.3.6`, Gradle `8.10.2`, Flyway Gradle plugin `10.10.0`, and H2 `2.2.224` are selected as concrete compatible patch/tool versions within the PRD-approved stack: `[AI-ASSUMPTION: Concrete compatible tool patch versions required for deterministic manifests | Attention Needed for Implementer]`.

---

## 4. Failure Protocol & Error Dynamics

Every error uses `Content-Type: application/problem+json` with `type`, `title`, `status`, `detail`, and `instance`. Validation failures additionally contain:

```json
{
  "invalidParams": [
    { "name": "fieldName", "reason": "validation message" }
  ]
}
```

| Category | HTTP | Trigger | Problem type / title |
|---|---:|---|---|
| Validation | 400 | DTO constraint failure, invalid E.164 after normalization | `urn:user-service:error:validation-failed` / `Validation Failed` |
| Malformed payload | 400 | Invalid JSON, date, or incompatible type | `urn:user-service:error:malformed-json` / `Malformed JSON Payload` |
| Invalid parent relation | 400 | Same parent IDs, self-parent, direct/indirect cycle | `urn:user-service:error:invalid-parent-relation` / `Invalid Parent Relationship` |
| User absent | 404 | User lookup/update/delete target missing or soft-deleted | `urn:user-service:error:user-not-found` / `User Not Found` |
| Parent absent | 404 | Assigned parent missing or soft-deleted | `urn:user-service:error:parent-not-found` / `Parent User Not Found` |
| Family anchor absent | 404 | No row ever existed for family endpoint ID | `urn:user-service:error:user-not-found` / `User Not Found` |
| Email conflict | 409 | Normalized email belongs to another active user | `urn:user-service:error:duplicate-email` / `Email Conflict` |
| Phone conflict | 409 | Normalized phone belongs to another active user | `urn:user-service:error:duplicate-phone` / `Phone Number Conflict` |

Soft-deleted email/phone reuse is not a conflict.

---

## 5. Infrastructure & Environment Manifest

This section is the sole file-generation source for `environment-bootstrap`.

### 5.1 Build Tool & Manifest Spec

#### Required files
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- Gradle wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Wrapper distribution: Gradle `8.10.2` binary distribution.

#### `settings.gradle.kts`
```kotlin
rootProject.name = "user-service"
```

#### `build.gradle.kts`
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.6"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.flywaydb.flyway") version "10.10.0"
}

group = "com.userservice"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("com.h2database:h2")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

flyway {
    url = "jdbc:h2:tcp://localhost:9092/./userservice"
    user = "sa"
    password = ""
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}
```

#### `gradle.properties`
```properties
org.gradle.jvmargs=-Dfile.encoding=UTF-8
org.gradle.parallel=true
```

### 5.2 Database Migration Spec
- **Migration tool**: Flyway.
- **Schema migration**: `src/main/resources/db/migration/V1__init_schema.sql`.
- **Seed migration**: `src/main/resources/db/migration/V2__seed_1000_users.sql`.
- Migrations run automatically at application startup and via `flywayMigrate`.

#### Exact `V1__init_schema.sql`
```sql
CREATE TABLE addresses (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    street VARCHAR(150) NOT NULL,
    building VARCHAR(50) NOT NULL,
    apartment VARCHAR(50),
    postal_code VARCHAR(20)
);

CREATE TABLE users (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(16),
    address_id BIGINT,
    father_id BIGINT,
    mother_id BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    active_email VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN is_deleted = FALSE THEN email ELSE NULL END),
    active_phone VARCHAR(16)
        GENERATED ALWAYS AS (CASE WHEN is_deleted = FALSE THEN phone_number ELSE NULL END),
    CONSTRAINT fk_users_address FOREIGN KEY (address_id) REFERENCES addresses(id),
    CONSTRAINT fk_users_father FOREIGN KEY (father_id) REFERENCES users(id),
    CONSTRAINT fk_users_mother FOREIGN KEY (mother_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX ux_users_active_email ON users(active_email);
CREATE UNIQUE INDEX ux_users_active_phone ON users(active_phone);
CREATE INDEX ix_users_father ON users(father_id);
CREATE INDEX ix_users_mother ON users(mother_id);
```

H2 generated active-key columns provide active-row-only uniqueness while allowing repeated values on soft-deleted rows.

#### Exact `V2__seed_1000_users.sql`
```sql
INSERT INTO addresses (id, country, city, street, building, apartment, postal_code)
SELECT x,
       CONCAT('Country ', x),
       CONCAT('City ', x),
       CONCAT('Street ', x),
       CAST(x AS VARCHAR),
       NULL,
       CONCAT('ZIP-', x)
FROM SYSTEM_RANGE(1, 20);

CREATE LOCAL TEMPORARY TABLE seed_address_distribution (
    address_id BIGINT PRIMARY KEY,
    first_user_id BIGINT NOT NULL,
    last_user_id BIGINT NOT NULL
);

INSERT INTO seed_address_distribution VALUES
 (1,1,110),(2,111,210),(3,211,300),(4,301,380),(5,381,450),
 (6,451,510),(7,511,565),(8,566,615),(9,616,660),(10,661,700),
 (11,701,735),(12,736,765),(13,766,790),(14,791,810),(15,811,828),
 (16,829,844),(17,845,858),(18,859,870),(19,871,880),(20,881,1000);

INSERT INTO users
    (id, first_name, last_name, birth_date, email, phone_number,
     address_id, father_id, mother_id, is_deleted)
SELECT x,
       CONCAT('First', x),
       CONCAT('Last', x),
       DATEADD('DAY', MOD(x, 3650), DATE '1940-01-01'),
       CONCAT('user', x, '@example.com'),
       CONCAT('+1555', RIGHT(CONCAT('0000000', x), 7)),
       (SELECT address_id FROM seed_address_distribution
         WHERE x BETWEEN first_user_id AND last_user_id),
       NULL, NULL, FALSE
FROM SYSTEM_RANGE(1, 100);

INSERT INTO users
    (id, first_name, last_name, birth_date, email, phone_number,
     address_id, father_id, mother_id, is_deleted)
SELECT x,
       CONCAT('First', x),
       CONCAT('Last', x),
       DATEADD('DAY', MOD(x - 101, 3650), DATE '1970-01-01'),
       CONCAT('user', x, '@example.com'),
       CONCAT('+1555', RIGHT(CONCAT('0000000', x), 7)),
       (SELECT address_id FROM seed_address_distribution
         WHERE x BETWEEN first_user_id AND last_user_id),
       1 + MOD(x - 101, 50),
       51 + MOD(x - 101, 50),
       FALSE
FROM SYSTEM_RANGE(101, 400);

INSERT INTO users
    (id, first_name, last_name, birth_date, email, phone_number,
     address_id, father_id, mother_id, is_deleted)
SELECT x,
       CONCAT('First', x),
       CONCAT('Last', x),
       DATEADD('DAY', MOD(x - 401, 7300), DATE '2000-01-01'),
       CONCAT('user', x, '@example.com'),
       CONCAT('+1555', RIGHT(CONCAT('0000000', x), 7)),
       (SELECT address_id FROM seed_address_distribution
         WHERE x BETWEEN first_user_id AND last_user_id),
       101 + MOD(x - 401, 150),
       251 + MOD(x - 401, 150),
       FALSE
FROM SYSTEM_RANGE(401, 1000);

DROP TABLE seed_address_distribution;
ALTER TABLE addresses ALTER COLUMN id RESTART WITH 21;
ALTER TABLE users ALTER COLUMN id RESTART WITH 1001;
```

This seed creates exactly 20 addresses and 1,000 active users, an uneven 10–120 users per address, and three chronological generations.

### 5.3 Runtime & Container Properties

#### Required configuration paths
- `src/main/resources/application.yml`
- `src/main/resources/application-docker.yml`
- `.dockerignore`
- `.gitignore`
- `Dockerfile`
- `docker/h2/Dockerfile`
- `docker-compose.yml`

#### `.gitignore`
```gitignore
.idea/
.gradle/
build/
data/
*.iml
.DS_Store
.ai/agents-config.local.md
```

#### `.dockerignore`
```dockerignore
.git
.gradle
.idea
.ai
.cursor
build
data
docs
src/test
*.md
```

#### `application.yml`
```yaml
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:h2:tcp://localhost:9092/./userservice
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
  mvc:
    problemdetails:
      enabled: true

server:
  port: 8080
```

#### `application-docker.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:tcp://h2-db:9092/./userservice
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080
```

#### `Dockerfile`
```dockerfile
FROM gradle:8.10.2-jdk21-alpine AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/user-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### `docker/h2/Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jre-alpine
ARG H2_VERSION=2.2.224
ADD https://repo1.maven.org/maven2/com/h2database/h2/${H2_VERSION}/h2-${H2_VERSION}.jar /opt/h2.jar
WORKDIR /data
EXPOSE 9092
CMD ["java", "-cp", "/opt/h2.jar", "org.h2.tools.Server", "-tcp", "-tcpAllowOthers", "-tcpPort", "9092", "-ifNotExists", "-baseDir", "/data"]
```

#### `docker-compose.yml`
```yaml
services:
  h2-db:
    build:
      context: ./docker/h2
    ports:
      - "9092:9092"
    volumes:
      - h2-data:/data
    healthcheck:
      test: ["CMD-SHELL", "java -cp /opt/h2.jar org.h2.tools.Shell -url jdbc:h2:tcp://localhost:9092/./userservice -user sa -sql 'SELECT 1'"]
      interval: 5s
      timeout: 5s
      retries: 12

  user-service:
    build:
      context: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      h2-db:
        condition: service_healthy

volumes:
  h2-data:
```

### 5.4 Directory Tree Specification
```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
├── Dockerfile
├── docker-compose.yml
├── docker/h2/Dockerfile
├── src/main/java/com/userservice/
│   ├── UserServiceApplication.java
│   ├── domain/
│   ├── dto/
│   ├── repository/
│   ├── service/
│   └── web/
├── src/main/resources/
│   ├── application.yml
│   ├── application-docker.yml
│   └── db/migration/
│       ├── V1__init_schema.sql
│       └── V2__seed_1000_users.sql
├── src/test/java/com/userservice/
└── src/test/resources/
```

---

## 6. Definition of Done
1. Every `[TARGET]` operation and invariant from PRD is represented.
2. `[BACKLOG]` behavior is absent from implementation contracts.
3. DTO schemas, side effects, failure mappings, and normalization rules are explicit.
4. Infrastructure manifest specifies every physical artifact required by `environment-bootstrap`.
5. `docs/PROJECT_ENV.md` supplies exact commands for downstream compilation and TDD phases.
