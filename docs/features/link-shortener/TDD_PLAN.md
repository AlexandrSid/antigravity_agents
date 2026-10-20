# TDD Plan: Link Shortener Service

## Overview
- **Feature Identifier**: `link-shortener`
- **Source Contract**: `docs/features/link-shortener/SPEC.md`
- **Engineering Standards**: `.ai/guidelines/core/architecture-standards.md`, `.ai/guidelines/stacks/frameworks/quarkus.md`, `.ai/guidelines/stacks/languages/java.md`
- **Author**: `tdd-planner` (sole creator; owns all task and test definitions)
- **Consumers**: `environment-bootstrap` (Step 03) · `skeleton-writer` (Step 04) · `test-writer` (Step 05) · `code-writer` (Step 06)

---

## 0. Type Surface & Skeleton Roadmap
Checklist of all DTOs, interfaces, entities, stubs, and components that `skeleton-writer` must place on disk before tests begin.

- [ ] Implement: Inbound DTO `CreateLinkRequest` (Java 21 record) with `@NotBlank`, `@Size(max = 2048)`, `@Pattern` URL validation constraints per SPEC §2.1
- [ ] Implement: Outbound DTO `LinkResponse` (Java 21 record) with `shortUrl`, `shortKey`, `originalUrl`, `createdAt`, `expiresAt` per SPEC §2.1
- [ ] Implement: Outbound DTO `ProblemDetailResponse` (Java 21 record) matching RFC 7807 (`type`, `title`, `status`, `detail`, `instance`, `invalidParams`) per `.ai/guidelines/core/architecture-standards.md`
- [ ] Implement: Domain Entity `ShortLink` (Quarkus PanacheEntityBase) with `id` (PK via `short_links_seq`), `shortKey`, `originalUrl`, `createdAt`, `expiresAt`
- [ ] Implement: Repository interface/class `ShortLinkRepository` implementing `PanacheRepository<ShortLink>` with method signatures:
  - `Optional<ShortLink> findByShortKey(String shortKey)`
  - `int deleteExpired(Instant now)`
- [ ] Implement: Utility / Service `ShortKeyGenerator` interface for Sqids key generation:
  - `String generateKey(long id)`
  - `OptionalLong decodeKey(String shortKey)`
- [ ] Implement: Cache component `RedisLinkCache` interface / stub:
  - `Optional<String> getLongUrl(String shortKey)`
  - `void putShortToLong(String shortKey, String longUrl, long ttlSeconds)`
  - `Optional<String> getShortKeyByLongUrl(String longUrl)`
  - `void putLongToShort(String longUrl, String shortKey, long ttlSeconds)`
  - `void refreshShortToLongTtl(String shortKey, long ttlSeconds)`
- [ ] Implement: Business Service interface `LinkService`:
  - `LinkResponse createShortLink(CreateLinkRequest request)`
  - `String resolveOriginalUrl(String shortKey)`
- [ ] Implement: Scheduled Job stub `TtlCleanupJob`:
  - `@Scheduled(cron = "{app.cleanup.cron}") void purgeExpiredLinks()`
- [ ] Implement: REST Resource stub `LinkResource` (`POST /api/v1/links`) returning empty / "Not Implemented"
- [ ] Implement: REST Resource stub `RedirectResource` (`GET /{shortKey}`) returning empty / "Not Implemented"

---

## 1. TDD Implementation Roadmap (Step-by-Step Micro-Tasks)
Sequential, atomic development steps. All tasks in Section 1 require **exclusively Unit / Slice tests** using in-memory components and mocked dependencies. External databases and containers are forbidden in this section.

---

### Task 1.1: Deterministic Key Generation (`ShortKeyGenerator`)
- **Step Goal**: Реализовать детерминированное двунаправленное кодирование ID в 8-символьный ключ с использованием библиотеки Sqids (`minLength = 8`).
- **Target Signature**: `ShortKeyGenerator#generateKey(long id): String` и `ShortKeyGenerator#decodeKey(String shortKey): OptionalLong`
- **Assumptions**: `[AI-ASSUMPTION: Sqids сконфигурирован с minLength=8 и стандартным алфавитом base62 | Attention Needed for Implementer: проверить алфавит без спецсимволов]`.
- **Red Phase (Unit Tests)**:
  - [ ] Test: `testGenerateKey_producesExact8Chars` — проверяет, что для любого ID >= 1 генерируется строка ровно из 8 буквенно-цифровых символов.
  - [ ] Test: `testGenerateKey_isDeterministic` — повторный вызов с одним и тем же ID возвращает идентичный `shortKey`.
  - [ ] Test: `testGenerateKey_differentIdsProduceDifferentKeys` — вызов для ID=1 и ID=2 возвращает уникальные разные ключи.
  - [ ] Test: `testDecodeKey_recoversOriginalId` — декодирование сгенерированного ключа восстанавливает исходный `id`.
  - [ ] Test: `testDecodeKey_invalidKeyReturnsEmpty` — некорректная строка или строка неверной длины возвращает `OptionalLong.empty()`.
- **Green Phase Guidance**: Обернуть `org.sqids.Sqids` с конфигурацией `minLength(8)`. Методы должны быть thread-safe и stateless.

---

### Task 1.2: Cache Logic & Key Hashing (`RedisLinkCache` & `CacheKeyUtil`)
- **Step Goal**: Реализовать логику формирования ключей Redis (включая SHA-256 хеширование длинных URL для исключения спецсимволов) и операции с Redis-клиентом.
- **Target Signature**: 
  - `CacheKeyUtil#shortKeyToLongKey(String shortKey): String`
  - `CacheKeyUtil#longUrlToShortKey(String longUrl): String` (с SHA-256 хешированием)
  - `RedisLinkCache#getLongUrl / putShortToLong / refreshShortToLongTtl`
- **Red Phase (Unit Tests)**:
  - [ ] Test: `testCacheKeyUtil_hashesLongUrlConsistently` — один и тот же URL дает идентичный детерминированный Redis-ключ с SHA-256 префиксом.
  - [ ] Test: `testGetLongUrl_cacheHit_returnsUrl` — при наличии ключа в Redis возвращается оригинальный URL.
  - [ ] Test: `testGetLongUrl_cacheMiss_returnsEmpty` — при отсутствии ключа возвращается `Optional.empty()`.
  - [ ] Test: `testPutShortToLong_setsKeyWithConfiguredTtl` — метод вызывает `setex` с заданным TTL (1 час при создании, 5 минут при чтении).
  - [ ] Test: `testRefreshShortToLongTtl_extendsTtl` — вызывает команду `expire` на 300 секунд (5 минут).
  - [ ] Test: `testPutLongToShort_sets30MinuteTtl` — связка long->short кешируется строго на 1800 секунд (30 минут).
  - [ ] Mock Behavior Guidance: Использовать мок `io.quarkus.redis.datasource.RedisDataSource` / `ValueCommands` без поднятия реального Redis.
- **Green Phase Guidance**: Реализовать `RedisLinkCache` поверх Quarkus Redis DataSource API с передачей параметров TTL из `application.properties`.

---

### Task 1.3: Link Creation & Idempotency Domain Service (`LinkService#createShortLink`)
- **Step Goal**: Бизнес-логика создания короткой ссылки с соблюдением 30-минутного окна идемпотентности, генерацией ID пачками и сохранением в БД и кеш.
- **Target Signature**: `LinkService#createShortLink(CreateLinkRequest request): LinkResponse`
- **Red Phase (Unit Tests)**:
  - [ ] Test: `testCreateShortLink_newUrl_reservesIdAndPersists` — если URL отсутствует в Redis `long->short`: резервирует ID, генерирует 8-значный ключ, сохраняет в репозиторий, прогревает оба кеша (short->long на 1ч, long->short на 30м), возвращает `201 Created` DTO.
  - [ ] Test: `testCreateShortLink_idempotentHit_returnsExistingAndExtendsTtl` — если URL найден в кеше `long->short` в пределах 30-минутного окна: НЕ генерирует новый ключ, находит существующую запись в БД, обновляет `expiresAt = NOW() + 24h`, возвращает DTO существующей ссылки.
  - [ ] Test: `testCreateShortLink_expiredIdempotency_generatesNewLink` — если 30 минут истекли (в кеше промах): генерирует новую отдельную ссылку даже для ранее встречавшегося URL.
  - [ ] Test: `testCreateShortLink_invalidUrlThrowsValidationException` — передача синтаксически невалидного URL выбрасывает доменное исключение валидации.
  - [ ] Mock Behavior Guidance: Замокать `ShortLinkRepository`, `RedisLinkCache`, `ShortKeyGenerator` с детерминированными ответами.
- **Green Phase Guidance**: Реализовать `LinkServiceImpl#createShortLink` с проверкой кэша идемпотентности перед обращением к Sequence БД.

---

### Task 1.4: Link Resolution & Cache-Aside Domain Service (`LinkService#resolveOriginalUrl`)
- **Step Goal**: Быстрое разрешение короткого ключа с Cache-Aside стратегией и автоматическим продлением TTL в кеше при каждом чтении.
- **Target Signature**: `LinkService#resolveOriginalUrl(String shortKey): String`
- **Red Phase (Unit Tests)**:
  - [ ] Test: `testResolve_cacheHit_refreshesTtlAndReturnsUrl` — при попадании в Redis: вызывает `refreshShortToLongTtl` (+5 минут) и немедленно возвращает `originalUrl` без обращения к БД.
  - [ ] Test: `testResolve_cacheMiss_readsDbAndPopulatesCacheWith5MinTtl` — при промахе в Redis: читает из `ShortLinkRepository`, если найдена и `expiresAt > NOW()` — сохраняет в Redis с TTL 5 минут и возвращает URL.
  - [ ] Test: `testResolve_linkExpiredInDb_throwsNotFoundException` — если запись найдена в БД, но `expiresAt <= NOW()` — возвращает ошибку 404 / выбрасывает `LinkNotFoundException`.
  - [ ] Test: `testResolve_linkNotFoundInDb_throwsNotFoundException` — если запись отсутствует в БД — выбрасывает `LinkNotFoundException`.
  - [ ] Mock Behavior Guidance: Замокать `RedisLinkCache` и `ShortLinkRepository`.
- **Green Phase Guidance**: Реализовать `LinkServiceImpl#resolveOriginalUrl` со строгой проверкой времени истечения ссылки (`expiresAt`).

---

### Task 1.5: TTL Cleanup Scheduled Job (`TtlCleanupJob`)
- **Step Goal**: Фоновая очистка просроченных записей с изоляцией транзакционным замком PostgreSQL advisory lock.
- **Target Signature**: `TtlCleanupJob#purgeExpiredLinks(): int`
- **Red Phase (Unit Tests)**:
  - [ ] Test: `testPurgeExpiredLinks_acquiresLockAndDeletesExpired` — при успешном получении advisory lock вызывает `repository.deleteExpired(Instant.now())` и возвращает число удаленных записей.
  - [ ] Test: `testPurgeExpiredLinks_lockNotAcquired_skipsExecution` — если advisory lock уже занят другим инстансом (`pg_try_advisory_xact_lock` вернул `false`), метод пропускает удаление и корректно логирует пропуск без выброса ошибки.
  - [ ] Mock Behavior Guidance: Замокать `EntityManager` / нативный SQL-запрос advisory lock.
- **Green Phase Guidance**: Оформить метод `@Scheduled(cron = "{app.cleanup.cron}")` с аннотацией `@Transactional` и вызовом нативного запроса блокировки.

---

### Task 1.6: REST Controller & Error Handling Slice Tests (`LinkResource` & `RedirectResource`)
- **Step Goal**: Проверка HTTP-слоя, корректности статус-кодов (201, 200, 302, 400, 404) и формата ошибок RFC 7807 Problem Details.
- **Target Signature**: 
  - `POST /api/v1/links`
  - `GET /{shortKey}`
- **Red Phase (Slice Tests with RestAssured / QuarkusTest)**:
  - [ ] Test: `testPostLink_validUrl_returns201WithShortUrl` — валидный POST возвращает HTTP 201 Created и JSON с полями `shortUrl`, `shortKey`, `originalUrl`.
  - [ ] Test: `testPostLink_blankUrl_returns400ProblemDetails` — пустой URL возвращает HTTP 400 и RFC 7807 JSON с массивом `invalidParams`.
  - [ ] Test: `testPostLink_urlExceeding2048Chars_returns400ProblemDetails` — URL длиннее 2048 символов возвращает HTTP 400.
  - [ ] Test: `testGetShortKey_existingActive_returns302WithLocationHeader` — GET запрос возвращает HTTP 302 Found с заголовком `Location: {originalUrl}`.
  - [ ] Test: `testGetShortKey_nonExistingOrExpired_returns404ProblemDetails` — неизвестный или просроченный ключ возвращает HTTP 404 и `application/problem+json`.
  - [ ] Mock Behavior Guidance: Использовать `@InjectMock LinkService` для изоляции контроллеров от бизнес-слоя.
- **Green Phase Guidance**: Реализовать JAX-RS / Quarkus REST ресурсы и ExceptionMappers для формирования RFC 7807 Problem Details.

---

## 2. Deferred Integration & E2E Backlog
Checklist сценариев, требующих реальной инфраструктуры (PostgreSQL, Redis, Testcontainers, Docker Compose). Эти тесты реализуются на шагах 07 и 08 (`integration-test-writer` и `integration-code-writer`) и строго изолированы от Unit TDD цикла.

- [ ] Integration: Проверка реальной генерации ID из PostgreSQL Sequence `short_links_seq` с шагом выделения `allocationSize = 1000` под параллельной нагрузкой.
- [ ] Integration: Сквозная проверка миграции Flyway `V1__init_schema.sql` на чистой базе PostgreSQL 16 в Testcontainers.
- [ ] Integration: Проверка реального Redis 7 в Testcontainers: запись ключей, чтение, проверка истечения TTL и продления TTL.
- [ ] Integration: Конкурентный запуск `TtlCleanupJob` на 2 параллельных потоках/инстансах с проверкой, что `pg_try_advisory_xact_lock` предотвращает двойное удаление.
- [ ] Integration: Проверка структурированного JSON-логирования в stdout (соответствие требованиям ELK).
- [ ] E2E: Сквозной сценарий через Docker Compose: запуск полного стека (app + postgres + redis) -> `POST /api/v1/links` -> `GET /{shortKey}` с проверкой заголовка `Location` и HTTP 302.
