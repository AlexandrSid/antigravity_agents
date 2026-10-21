> [!WARNING]
> **AI USAGE RESTRICTION NOTICE**:
> This document is a human-readable summary compiled solely for human operators, engineers, and reviewers.
> AI agents MUST NOT use this file as a Single Source of Truth (SSOT) or as a technical reference.
> `docs/features/link-shortener/SPEC.md`, `docs/PRD.md`, and `docs/PROJECT_ENV.md` remain the only authoritative Single Sources of Truth.

# Feature Summary: Link Shortener

## 1. Overview
- **Feature Identifier**: `link-shortener`
- **Primary Contract**: [`docs/features/link-shortener/SPEC.md`](file:///c:/Users/Aleksandr/AI%20projects/antigravity_agents/docs/features/link-shortener/SPEC.md)
- **Execution Plan**: [`docs/features/link-shortener/TDD_PLAN.md`](file:///c:/Users/Aleksandr/AI%20projects/antigravity_agents/docs/features/link-shortener/TDD_PLAN.md)
- **Status**: Completed / Ready for Review
- **Target Scope**: REST API для создания 8-символьных коротких ссылок с 30-минутным окном идемпотентности, HTTP 302 редиректом, автоматической фоновой очисткой просроченных ссылок по расписанию (advisory lock), двухуровневым кешированием в Redis и персистентным хранением в PostgreSQL.

## 2. Implemented Capabilities & Endpoints

### 2.1 `POST /api/v1/links` — Создание короткой ссылки
- **Назначение**: Принимает исходный URL, валидирует схему и длину (до 2048 символов), резервирует ID через PostgreSQL Sequence (`short_links_seq`, шаг 1000), генерирует 8-символьный Sqids-ключ и сохраняет запись в PostgreSQL.
- **Идемпотентность (окно 30 минут)**: Повторный запрос с тем же URL в течение 30 минут не создает новую запись, а возвращает существующий шортлинк со статусом `200 OK` и обновляет его TTL в БД (24 часа от момента вызова) и Redis.
- **Request Body**:
  ```json
  {
    "url": "https://example.com/long/url"
  }
  ```
- **Responses**:
  - `201 Created` (новая ссылка):
    ```json
    {
      "shortUrl": "https://shortner.url/aB3dEfGh",
      "shortKey": "aB3dEfGh",
      "originalUrl": "https://example.com/long/url",
      "createdAt": "2026-10-21T18:00:00Z",
      "expiresAt": "2026-10-22T18:00:00Z"
    }
    ```
  - `200 OK` (идемпотентный возврат существующей ссылки с обновленным TTL).
  - `400 Bad Request` (RFC 7807 Problem Details при невалидном URL или превышении 2048 символов).

### 2.2 `GET /{shortKey}` — Редирект по ключу
- **Назначение**: Поиск оригинального URL по 8-символьному ключу и перенаправление клиента.
- **Стратегия кеширования**:
  1. Чтение из Redis по ключу `short:{shortKey}`. При hit — продление TTL в Redis на 5 минут (`EXPIRE short:{shortKey} 300`) и немедленный редирект.
  2. При cache miss — чтение из PostgreSQL. Если ссылка существует и не истекла, запись помещается в Redis с TTL 5 минут.
- **Responses**:
  - `302 Found` с заголовком `Location: {originalUrl}`.
  - `404 Not Found` (RFC 7807 Problem Details) если ключ не найден в БД или срок действия ссылки (`expires_at`) истек.

### 2.3 Фоновая задача очистки (`TtlCleanupJob`)
- **Расписание**: Ежедневно в 00:00 (cron `0 0 0 * * ?`, конфигурируется через `app.cleanup.cron`).
- **Синхронизация в кластере**: Вызов `pg_try_advisory_xact_lock(748392019)` в рамках одной транзакции, гарантирующий запуск очистки строго на одном узле при горизонтальном масштабировании.

## 3. Engineering Invariants Verification

- **Архитектурное расслоение (Quarkus / Hexagonal)**: Четкое разделение слоев: Resource (`LinkResource`, `RedirectResource`) -> Service (`LinkServiceImpl`) -> Repository/Entity (`ShortLink`) -> Cache (`RedisCacheService`).
- **RFC 7807 Error Handling**: Все исключения маппятся глобальным `GlobalExceptionMapper` в стандартизированный формат `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`, `invalidParams`).
- **Детерминированная генерация ключей (Sqids)**: Ключ формируется на основе гарантированно уникального ID последовательности `short_links_seq` с минимальной длиной 8 символов.
- **Удаление стабов (Anti-Stub Quality Gate)**: Задача Gradle `checkNoStubs` подтверждает отсутствие `TODO`, `FIXME`, и `UnsupportedOperationException` в продакшен-коде (`src/main/**`).
- **Структурированное логирование**: Консольный вывод Quarkus формируется в JSON-формате (`quarkus.log.console.json=true`), совместимом с ingestion-конвейерами ELK/OpenSearch.

## 4. Verification Evidence

| Тестовый набор / Gate | Выполненная команда | Результат | Статус |
|---|---|---|:---:|
| **Unit & Slice Tests** | `.\gradlew.bat test --no-daemon` | 27 тестов успешно, 0 ошибок | `PASSED` |
| **Stub Eradication Gate** | `.\gradlew.bat checkNoStubs --no-daemon` | 0 фиктивных заглушек в `src/main/**` | `PASSED` |
| **Integration Suite (Testcontainers)** | `.\gradlew.bat integrationTest --no-daemon` | 4 интеграционных сценария успешно (PostgreSQL sequence & Flyway, Redis caching & TTL touch, Advisory lock concurrency, JSON logging) | `PASSED` |
| **Black-box Docker Compose E2E** | `python scripts/run_compose_e2e.py` | Сквозной цикл: сборка jar -> запуск контейнеров -> опрос готовности -> POST 201 -> GET 302 -> 404 -> очистка томов | `PASSED` |
| **Quality Gate: Unit Green** | `python python-runtime/gate_runner.py . UNIT_GREEN_PHASE` | Код выхода 0 | `PASSED` |
| **Quality Gate: Integration Green** | `python python-runtime/gate_runner.py . INTEGRATION_GREEN_PHASE` | Код выхода 0 | `PASSED` |

## 5. Configuration & Runtime Prerequisites

### 5.1 Системные требования
- **JDK**: Java 21 LTS (`JAVA_HOME` указывает на JDK 21).
- **Сборщик**: Gradle Wrapper 8.10 (`gradlew` / `gradlew.bat`).
- **Контейнеризация**: Docker Engine / Docker Desktop (с поддержкой Docker Compose v2).

### 5.2 Ключевые параметры конфигурации (`application.properties`)
| Параметр | Значение по умолчанию | Описание |
|---|---|---|
| `quarkus.http.port` | `8080` | Порт HTTP-сервера приложения |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:linkshortener}` | Подключение к PostgreSQL |
| `quarkus.redis.hosts` | `redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}` | Хост Redis |
| `app.base-url` | `https://shortner.url` | Базовый домен для формирования поля `shortUrl` |
| `app.link.ttl-hours` | `24` | Время жизни ссылки в БД (по умолчанию 24 часа) |
| `app.cache.short-to-long.create-ttl-seconds` | `3600` | TTL кеша `shortKey -> originalUrl` при создании (1 час) |
| `app.cache.short-to-long.read-ttl-seconds` | `300` | TTL кеша `shortKey -> originalUrl` при чтении (5 минут) |
| `app.cache.long-to-short.ttl-seconds` | `1800` | TTL кеша `originalUrl -> shortKey` для окна идемпотентности (30 минут) |
| `app.sqids.min-length` | `8` | Минимальная длина ключа Sqids |
| `app.sequence.allocation-size` | `1000` | Размер пакета аллокации ID из `short_links_seq` |
| `app.cleanup.cron` | `0 0 0 * * ?` | Cron-расписание очистки просроченных ссылок |

### 5.3 Команды управления жизненным циклом
- **Сборка приложения**: `.\gradlew.bat build -x test --no-daemon`
- **Запуск unit-тестов**: `.\gradlew.bat test --no-daemon`
- **Запуск интеграционных тестов**: `.\gradlew.bat integrationTest --no-daemon`
- **Сквозная проверка E2E (Docker Compose)**: `python scripts/run_compose_e2e.py`
- **Запуск всего стека**: `docker compose -p link-shortener up -d --build --force-recreate`
- **Остановка стека с удалением томов**: `docker compose -p link-shortener down -v --remove-orphans`

## 6. Deferred & Out-of-Scope Items
Следующие функциональности согласованы как выходящие за рамки текущего этапа (`Scope: [BACKLOG]` в `docs/PRD.md`):
1. **Повторное использование освободившихся ключей**: Пул переиспользования ключей после удаления записей фоновой задачей.
2. **Пользовательские алиасы (Custom Aliases)**: Ручной ввод желаемого `shortKey` клиентом.
3. **Аналитика переходов и авторизация**: Сбор метрик кликов, географии, рефереров и ролевая модель доступа.
