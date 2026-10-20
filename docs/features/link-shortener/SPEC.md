# Feature Specification: Link Shortener

## 1. Overview & Communication Pattern

- **Feature Identifier**: `link-shortener`
- **Target Scope**: REST API для создания 8-символьных коротких ссылок с идемпотентным окном 30 минут, HTTP-редиректом по ключу, фоновой TTL-очисткой с advisory lock-изоляцией, кешированием через Redis и хранилищем PostgreSQL.
- **Communication Pattern**: Synchronous Request/Reply (REST HTTP) + Background Scheduled Job
- **Participants**:
  - Инициаторы: внешние/внутренние HTTP-клиенты, M2M-сервисы, планировщик (TTL Job)
  - Обработчики: `LinkResource` (REST), `RedirectResource` (GET), `TtlCleanupJob` (Quarkus Scheduler)

## Applicable Technical Standards

- `.ai/guidelines/core/architecture-standards.md`
- `.ai/guidelines/core/database-migrations.md`
- `.ai/guidelines/core/deployment-artifacts.md`
- `.ai/guidelines/stacks/frameworks/quarkus.md`
- `.ai/guidelines/stacks/languages/java.md`
- `.ai/guidelines/stacks/databases/postgresql.md`
- `.ai/guidelines/stacks/infrastructure/docker-compose.md`

---

## 2. Interface & Interaction Contract

### 2.1 Endpoint: Create Short Link

- **Target**: `POST /api/v1/links`
- **Interaction Semantics**: Synchronous Request/Reply
- **Transport & Wire Format**: HTTP/1.1, JSON (`Content-Type: application/json`)

#### Inbound Schema (Request Body)

```json
{
  "url": "https://example.com/very/long/path?with=params"
}
```

| Field | Type     | Required | Constraints                                  |
|-------|----------|----------|----------------------------------------------|
| `url` | `String` | Yes      | Valid URL (scheme + host); max 2048 chars     |

#### Outbound Schema (Response Body — 201 Created)

```json
{
  "shortUrl": "https://shortner.url/aB3dEfGh",
  "shortKey": "aB3dEfGh",
  "originalUrl": "https://example.com/very/long/path?with=params",
  "createdAt": "2026-10-20T13:00:00Z",
  "expiresAt": "2026-10-21T13:00:00Z"
}
```

| Field         | Type              | Description                              |
|---------------|-------------------|------------------------------------------|
| `shortUrl`    | `String`          | Полный short URL для редиректа           |
| `shortKey`    | `String`          | 8-символьный ключ                        |
| `originalUrl` | `String`          | Исходный URL                             |
| `createdAt`   | `String (ISO8601)`| Время создания                           |
| `expiresAt`   | `String (ISO8601)`| Время истечения (createdAt + 24h)        |

**Идемпотентность**: если тот же `url` был сокращён менее 30 минут назад — возвращается **200 OK** с существующим шортлинком и обновлённым `expiresAt` (TTL сбрасывается).

---

### 2.2 Endpoint: Redirect by Short Key

- **Target**: `GET /{shortKey}`
- **Interaction Semantics**: Synchronous Request/Reply with redirect
- **Transport & Wire Format**: HTTP/1.1

#### Inbound

| Parameter  | Location    | Type     | Required | Constraints    |
|------------|-------------|----------|----------|----------------|
| `shortKey` | Path param  | `String` | Yes      | Exactly 8 chars, alphanumeric |

#### Outbound

- **Success**: `302 Found` с заголовком `Location: {originalUrl}`
  - [AI-ASSUMPTION: используется 302, а не 301, чтобы избежать агрессивного кеширования браузером и обеспечить гибкость изменения маппинга. | Attention Needed for Implementer: уточнить с командой SEO-требования — при необходимости 301 требует явного изменения в конфиге.]
- **Not Found / Expired**: `404 Not Found` с RFC 7807 Problem Details

---

### 2.3 Side-Effects, State Mutations & Events

**POST /api/v1/links (новая ссылка):**
1. Резервирует следующий ID из PostgreSQL Sequence (пачками по 1000, `allocationSize=1000`).
2. Вычисляет `shortKey = Sqids.encode(id)` (8 символов).
3. Сохраняет запись `ShortLink` в PostgreSQL.
4. Записывает в Redis кеш:
   - `short:{shortKey}` → `{originalUrl}`, TTL = 1 час
   - `long:{sha256(originalUrl)}` → `{shortKey}`, TTL = 30 минут
5. Возвращает 201 Created.

**POST /api/v1/links (идемпотентный — URL уже существует в окне 30 мин):**
1. Ищет `long:{sha256(originalUrl)}` в Redis → hit.
2. Обновляет `ttl` записи в PostgreSQL (сбрасывает на 24 часа от NOW).
3. Продлевает TTL в Redis: `short:{shortKey}` → +5 мин (или 1 час), `long:...` → 30 мин.
4. Возвращает 200 OK с существующей записью.

**GET /{shortKey}:**
1. Проверяет Redis `short:{shortKey}` → hit: продлевает TTL на 5 мин, выполняет 302-редирект.
2. Cache miss: читает из PostgreSQL, кладёт в Redis с TTL = 5 мин, выполняет 302-редирект.

**TtlCleanupJob (ежедневно 00:00):**
1. Получает PostgreSQL advisory lock (pg_try_advisory_xact_lock).
2. Удаляет все записи, где `expires_at < NOW()`.
3. Освобождает lock автоматически по завершении транзакции.

---

## 3. Domain Model & Validation Matrix

| Entity       | Field         | Type                   | Required | Constraints & Domain Invariants                                                  | Status       |
|--------------|---------------|------------------------|----------|----------------------------------------------------------------------------------|--------------|
| `ShortLink`  | `id`          | `Long` (PK)            | Yes      | Auto-generated via Sequence (allocationSize=1000); never exposed in API response | Confirmed    |
| `ShortLink`  | `shortKey`    | `String`               | Yes      | Exactly 8 chars, alphanumeric; unique; derived from `id` via Sqids               | Confirmed    |
| `ShortLink`  | `originalUrl` | `String`               | Yes      | Valid URL; max 2048 chars; not empty                                              | Confirmed    |
| `ShortLink`  | `createdAt`   | `Instant`              | Yes      | Set at creation, immutable                                                        | Confirmed    |
| `ShortLink`  | `expiresAt`   | `Instant`              | Yes      | `createdAt + 24h` by default; updated on idempotent POST                         | Confirmed    |
| `CreateLinkRequest` | `url` | `String`               | Yes      | Valid URL; max 2048 chars                                                         | Confirmed    |
| `LinkResponse` | `shortUrl`  | `String`               | Yes      | `{BASE_URL}/{shortKey}`, read-only DTO                                           | Confirmed    |

> [AI-ASSUMPTION: `shortKey` вычисляется как `Sqids.encode(id)` и имеет фиксированную длину 8 символов через конфигурацию `minLength=8`. | Attention Needed for Implementer: выбрать конкретную Java-библиотеку Sqids и зафиксировать параметры алфавита/минимальной длины в конфиге.]

> [AI-ASSUMPTION: идентификация записи для идемпотентности выполняется через SHA-256 хеш `originalUrl` в Redis ключе `long:{hash}`. | Attention Needed for Implementer: убедиться что SHA-256 достаточно для исключения коллизий в данном контексте.]

---

## 4. Failure Protocol & Error Dynamics

Все ошибки возвращаются в формате **RFC 7807 Problem Details** (`Content-Type: application/problem+json`).

| Error Category              | HTTP Status | Trigger Condition                                          | Error Payload                                                                 |
|-----------------------------|-------------|------------------------------------------------------------|-------------------------------------------------------------------------------|
| Bad Input / Validation      | `400`       | `url` пустой, невалидный URL, превышает 2048 символов      | `{ type, title, status: 400, detail, instance, invalidParams: [{name, reason}] }` |
| Target Absence              | `404`       | `shortKey` не найден в БД или запись просрочена (expires_at < NOW) | `{ type, title, status: 404, detail, instance }`                    |
| Internal / Infrastructure   | `503`       | PostgreSQL или Redis недоступны                            | `{ type, title, status: 503, detail, instance }`                              |

---

## 5. Infrastructure & Environment Manifest

*(SSOT for `environment-bootstrap`)*

### 5.1 Build Tool & Manifest Spec

- **Build tool file**: `build.gradle.kts`
- **Runtime**: Java 21 LTS (`JAVA_HOME` must point to JDK 21)
- **Build wrapper**: `gradlew` / `gradlew.bat` — сгенерировать через Quarkus CLI или Gradle wrapper task
- **Quarkus version**: `3.15.x` (latest stable 3.x)
- **Gradle wrapper version**: `8.10`

**Quarkus Extensions (зависимости):**

```kotlin
// build.gradle.kts — dependencies block
implementation("io.quarkus:quarkus-rest-jackson")           // REST + Jackson JSON
implementation("io.quarkus:quarkus-hibernate-orm-panache")  // ORM + Panache
implementation("io.quarkus:quarkus-flyway")                 // DB migrations
implementation("io.quarkus:quarkus-jdbc-postgresql")        // PostgreSQL JDBC
implementation("io.quarkus:quarkus-redis-client")           // Redis client
implementation("io.quarkus:quarkus-scheduler")              // Quartz scheduler (TTL job)
implementation("io.quarkus:quarkus-smallrye-openapi")       // OpenAPI / Swagger UI
implementation("io.github.sqids:sqids:0.1.0")               // Sqids key generation

// Test dependencies
testImplementation("io.quarkus:quarkus-junit5")
testImplementation("io.rest-assured:rest-assured")
testImplementation("org.mockito:mockito-core")
testImplementation("io.quarkus:quarkus-test-h2")            // unit slice tests with H2

// Integration test dependencies
testImplementation("org.testcontainers:postgresql")
testImplementation("org.testcontainers:junit-jupiter")
// Redis testcontainers (via generic container)
```

- **Quarkus package type**: `fast-jar` (обязательно: `.ai/guidelines/stacks/frameworks/quarkus.md`)
- **Logging format**: JSON structured logging через `quarkus-logging-json` extension

---

### 5.2 Database Migration Spec

- **Migration tool**: Flyway (встроен в `quarkus-flyway`)
- **DDL file path**: `src/main/resources/db/migration/V1__init_schema.sql`

```sql
-- V1__init_schema.sql

-- Short links table
CREATE TABLE short_links (
    id          BIGINT       NOT NULL,
    short_key   VARCHAR(8)   NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_short_links PRIMARY KEY (id),
    CONSTRAINT uq_short_links_short_key UNIQUE (short_key)
);

-- Index for fast lookup by short_key (covered by UNIQUE constraint, explicit for clarity)
CREATE INDEX idx_short_links_short_key ON short_links (short_key);

-- Index for idempotency lookup by original_url hash (stored as generated column)
-- Note: PostgreSQL 12+ supports generated columns.
-- Hash stored separately for fast exact match, original_url kept for retrieval.
CREATE INDEX idx_short_links_expires_at ON short_links (expires_at);

-- Sequence for ID generation with allocation size 1000
-- Quarkus Hibernate ORM Panache will use this sequence via @SequenceGenerator
CREATE SEQUENCE short_links_seq
    START WITH 1
    INCREMENT BY 1000
    NO CYCLE;
```

> **Note on Sequence**: Hibernate/Panache's `allocationSize=1000` means the app reserves blocks of 1000 IDs from `short_links_seq` (which increments by 1000 per nextval call). The `id` column values are dense within each block but may have gaps between blocks — this is expected and correct.

---

### 5.3 Runtime & Container Properties

#### `src/main/resources/application.properties`

```properties
# === Application ===
quarkus.application.name=link-shortener

# === Package ===
quarkus.package.jar.type=fast-jar

# === HTTP ===
quarkus.http.port=8080

# === DataSource (PostgreSQL) ===
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USER:linkshortener}
quarkus.datasource.password=${DB_PASSWORD:linkshortener}
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:linkshortener}

# === Hibernate ORM ===
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false

# === Flyway ===
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration

# === Redis ===
quarkus.redis.hosts=redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}

# === Logging (JSON structured for ELK) ===
quarkus.log.console.json=true
quarkus.log.console.json.pretty-print=false

# === OpenAPI ===
quarkus.smallrye-openapi.path=/openapi
quarkus.swagger-ui.always-include=true

# === App-level config (all TTLs and invariants) ===
app.base-url=https://shortner.url
app.link.ttl-hours=24
app.cache.short-to-long.create-ttl-seconds=3600
app.cache.short-to-long.read-ttl-seconds=300
app.cache.long-to-short.ttl-seconds=1800
app.sqids.min-length=8
app.sequence.allocation-size=1000
app.cleanup.cron=0 0 0 * * ?
```

#### `src/main/resources/application-test.properties` (для Unit/Slice тестов)

```properties
# H2 in-memory for unit/slice tests
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.flyway.migrate-at-start=false
# Disable Redis for unit tests (mock via @InjectMock)
quarkus.redis.hosts=redis://localhost:6379
```

#### `Dockerfile`

```dockerfile
# === Stage 1: Build ===
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# === Stage 2: Runtime (Quarkus Fast-JAR) ===
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /deployments
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/*.jar /deployments/
COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/app/ /deployments/app/
COPY --chown=appuser:appgroup --from=builder /build/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

#### `docker-compose.yml`

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: linkshortener
      POSTGRES_USER: linkshortener
      POSTGRES_PASSWORD: linkshortener
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 3s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 3s
      timeout: 3s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: linkshortener
      DB_USER: linkshortener
      DB_PASSWORD: linkshortener
      REDIS_HOST: redis
      REDIS_PORT: 6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

volumes:
  postgres_data:
```

---

### 5.4 Directory Tree Specification

```
link-shortener/                               ← project root
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── Dockerfile
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── linkshortener/
│   │   │           ├── domain/
│   │   │           │   └── ShortLink.java              ← Hibernate/Panache entity
│   │   │           ├── dto/
│   │   │           │   ├── CreateLinkRequest.java      ← Java record (inbound DTO)
│   │   │           │   └── LinkResponse.java           ← Java record (outbound DTO)
│   │   │           ├── repository/
│   │   │           │   └── ShortLinkRepository.java    ← PanacheRepository
│   │   │           ├── service/
│   │   │           │   ├── LinkService.java            ← business logic interface
│   │   │           │   └── LinkServiceImpl.java        ← implementation
│   │   │           ├── cache/
│   │   │           │   ├── RedisLinkCache.java         ← Redis operations
│   │   │           │   └── CacheKeyUtil.java           ← key construction + SHA-256
│   │   │           ├── keygen/
│   │   │           │   └── ShortKeyGenerator.java      ← Sqids wrapper
│   │   │           ├── job/
│   │   │           │   └── TtlCleanupJob.java          ← @Scheduled + advisory lock
│   │   │           └── resource/
│   │   │               ├── LinkResource.java           ← POST /api/v1/links
│   │   │               └── RedirectResource.java       ← GET /{shortKey}
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/
│   │           └── migration/
│   │               └── V1__init_schema.sql
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── linkshortener/
│       │           ├── service/
│       │           │   └── LinkServiceTest.java        ← Unit tests (Mockito)
│       │           ├── cache/
│       │           │   └── RedisLinkCacheTest.java     ← Cache logic tests
│       │           ├── keygen/
│       │           │   └── ShortKeyGeneratorTest.java  ← Deterministic key gen tests
│       │           ├── job/
│       │           │   └── TtlCleanupJobTest.java      ← Advisory lock logic tests
│       │           └── resource/
│       │               └── LinkResourceTest.java       ← @QuarkusTest REST slice tests
│       └── resources/
│           └── application-test.properties
└── src/
    └── integrationTest/
        ├── java/
        │   └── com/
        │       └── linkshortener/
        │           └── LinkShortenerIntegrationTest.java  ← Testcontainers PG+Redis
        └── resources/
```
