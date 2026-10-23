# Roadmap проекта Anti-Gravity Agents

## 🟢 Фаза 1: Мультиагентный фундамент и ручной Greenfield SDLC (MVP)
> **Статус**: Выполнено (`[x]`)  
> **Результат**: Ролевая модель специализированных агентов, детерминированный регламент конвейера, передача состояния через артефакты и валидация на пилотном сервисе User Service (ветка `stage1_mvp`).

### Стадия 1.1: Контракты агентов и передача состояния
- [x] **Ролевая модель и конвейер**: Разработка 9 специализированных ролей (`.ai/rules/`) с жесткими границами записи, входами/выходами и регламентом `.ai/GREENFIELD_SDLC_PIPELINE.md`.
- [x] **Передача состояния через файлы**: Вся передача данных между шагами происходит исключительно через файлы-артефакты (`docs/features/{feature}/PRD.md`, `docs/features/{feature}/SPEC.md`, `docs/features/{feature}/TDD_PLAN.md`, `docs/features/{feature}/INTEGRATION_REPORT.md`, `docs/PROJECT_ENV.md`, `docs/features/{feature}/SUMMARY.md`).
- [x] **Формирование контекстного среза (концептуальное)**: Определение минимального окна вызова (системная роль + входные артефакты шага + точный промпт-задание).

### Стадия 1.2: Маршрутизация моделей и профили задач
- [x] **Маппинг ролей и профилей**:
  - `REASONING_HEAVY` (старшие reasoning-модели) — системный анализ, архитектура (`architect`), TDD-планирование (`tdd-planner`).
  - `STRICT_CODEGEN` (старшие кодовые модели) — строгая кодогенерация тестов и реализации (`test-writer`, `code-writer`, `integration-test-writer`, `integration-code-writer`).
  - `FAST_EXECUTION` (быстрые легковесные модели) — окружение, каркас и документация (`environment-bootstrap`, `skeleton-writer`, `documenter`).
- [x] **Спецификация конфигураций**: Описание правил маршрутизации в `.ai/agents-config.md` и поддержка локальных переопределений в `.ai/agent-config.local.md`.

### Стадия 1.3: Ручная оркестрация и сквозная верификация (MVP)
- [x] **IDE Ручной режим (Ad-hoc)**: Поддержка ручных вызовов конкретного агента через переключение чата IDE и передачу контекстных тегов (`@.ai/rules/...`).
- [x] **Пилотный User Service MVP (ветка `stage1_mvp`)**: Сквозная реализация микросервиса (Java 21 / Spring Boot / Flyway / H2 / Docker Compose) с прохождением Unit/Slice и Integration/E2E контрольных этапов (Quality Gates).

---

## 🟢 Фаза 2: Скриптинг автозапуска, изоляция контекстов и управление состоянием (Orchestrator Core)
> **Статус**: Выполнено (`[x]`)  
> **Результат**: Двухрежимный рантайм (Python CLI Core + In-IDE Orchestrator), чистая изоляция контекстов (Stateless Execution), Bounded Tool Shell с контролем Write Scope, парсинг пайплайнов из Markdown SSOT, динамические Quality Gates с TDD Red/Green семантикой, Git Checkpoints & Rollback, трекинг токенов/стоимости (`EXECUTION_TRACE.json`) и подсистема профилирования с хуками IDE (ветка `stage_2_improvements`).  
> **Техническая спецификация**: [.ai/specs/STAGE_2_SPEC.md](file:///.ai/specs/STAGE_2_SPEC.md) | [.ai/specs/STAGE_2.1_IMPROVE.md](file:///.ai/specs/STAGE_2.1_IMPROVE.md)

### Стадия 2.1: Изоляция контекста и механизм взаимодействия (Execution Engine Protocol)
- [x] **Архитектура чистого старта (Stateless Execution)**: Каждый вызов агента происходит в изолированном контекстном окне, формируемом строго из 3 элементов: системная роль (`.ai/rules/{agent}.md`), входные артефакты шага по пайплайну и точный промпт-задание.
- [x] **Детерминированный Bounded Local Tool Shell**: Предоставление агенту стандартизированного минимального набора локальных инструментов (`read_artifact`, `apply_patch`, `complete_step`) через JSON-Schema Tool Calling или текстовый DSL-протокол для моделей без нативных инструментов (`python-runtime/tool_shell.py`).
- [x] **Контроль границ записи (Write Scope Enforcement)**: Проверка пути каждого файла при вызове `apply_patch` на соответствие разрешённому Write Scope из роли агента (`.ai/rules/{agent}.md`). При нарушении — отказ инструмента (`Permission denied: File outside Write Scope`).
- [x] **Ограничение ReAct-цикла (Bounded Iterations)**: Лимит максимум 5 итераций вызова инструментов на один шаг для предотвращения зацикливания и перерасхода токенов.

### Стадия 2.2: Динамическая маршрутизация моделей и манифест (Dynamic LLM Binding & Manifest)
- [x] **Парсинг конфигураций (TOML in Markdown)**: Программный парсинг секций профилей (`[REASONING_HEAVY]`, `[STRICT_CODEGEN]`, `[FAST_EXECUTION]`) с параметрами `provider`, `preferred_model`, `fallback_model`, `temperature`, `max_output_tokens` из `.ai/agents-config.md` с приоритетом локального `.ai/agent-config.local.md` (`python-runtime/config_resolver.py`).
- [x] **Динамический биндинг провайдеров**: Подключение сред исполнения (`native` Antigravity/Cursor/Claude Code) и интерфейсов внешних/локальных шлюзов (Ollama, LM Studio) в соответствии с назначенными профилями.

### Стадия 2.3: Двухрежимная оркестрация и парсинг пайплайна (Dual-Mode & Pipeline Parsing)
- [x] **Парсинг пайплайна из Markdown (SSOT)**: Прямой парсер таблицы шагов из `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md` с валидацией схемы колонок (`Step`, `Agent Role`, `Inputs`, `Outputs`, `Model Profile`, `Gate`). Исключение дублирующих JSON/YAML файлов (`python-runtime/pipeline_parser.py`).
- [x] **CLI Авто-режим с обязательным флагом `--feature`**: Сквозной запуск сценария:
  `python python-runtime/standalone_runner.py --pipeline .ai/pipelines/GREENFIELD_SDLC_PIPELINE.md --feature <name> --mode auto`
- [x] **IDE Ручной и Оркестрируемый режим**: Сохранение ручного вызова через IDE и поддержка внутри-IDE оркестратора (`.agents/skills/sdlc-orchestrator/SKILL.md`, `.antigravity/orchestrator.md`, `.cursor/rules/orchestrator.md`).
- [x] **Точки останова (Interactive Breakpoints)**: Поддержка интерактивного режима (`--mode interactive` в standalone runner и интерактивное подтверждение шагов в IDE-оркестраторе).
- [x] **Human-in-the-Loop Fallback**: При исчерпании лимита самоисправления (3 неудачных попытки) переход в интерактивную паузу с меню:
  `[1] Retry manually in IDE (Pause & Wait)`, `[2] Force retry with elevated reasoning model`, `[3] Abort pipeline execution`.

### Стадия 2.4: Безопасность, TDD Quality Gates, Rollbacks и Observability
- [x] **Динамический резолвинг команд Quality Gates**: Парсинг секции `Execution & Verification Commands` из `PROJECT_ENV.md` / манифеста окружения и связывание колонок `Gate` из пайплайна с реальными командами сборщика (`COMPILE_CHECK`, `UNIT_RED_PHASE`, `UNIT_GREEN_PHASE`, `INTEGRATION_GREEN_PHASE`) в `python-runtime/gate_runner.py`.
- [x] **Раздельная TDD-семантика проверок**:
  - `UNIT_RED_PHASE` (Шаг 05): компиляция успешна (exit code = 0), падение тестов вызвано исключительно `AssertionError` / `UnsupportedOperationException`.
  - `GREEN_PHASE` (Шаги 06, 08): компиляция успешна и 100% тестов пройдены (exit code = 0).
- [x] **Git Checkpoints & Self-Correction Loop**: Создание снимка Git (`git stash create` / Commit SHA) перед шагом. При провале Gate — `git reset --hard <checkpoint_sha>` и до 3 попыток ретрая с передачей стектрейса/лога ошибки агенту (`python-runtime/git_engine.py`).
- [x] **Защита веток и авто-ветвление (Branch Protection)**: Работа исключительно в ветках вида `agent-feature-{feature}` (или `agent-bugfix-*`). Поддержка флага `--git-auto` (автосоздание и переключение на ветку) или блокировка запуска на `main`/`master`/`stage*` с подсказкой команды. Пошаговые коммиты успешных шагов.
- [x] **Cost & Execution Tracking (`EXECUTION_TRACE.json`)**: Генерация посессионного трейса `docs/features/{feature}/EXECUTION_TRACE.json` с фиксацией времени начала/окончания, длительности, токенов и расчетной стоимости `cost_usd` по встроенному Price Registry (для облачных моделей) и 0.0 (для локальных моделей).

---

## 🟠 Фаза 3: Декомпозиция TDD и масштабирование тестов (Master-Worker Pattern)
> **Статус**: В плане  
> **Цель**: Избавиться от перерасхода токенов и падения качества генерации при написании больших наборов тестов.

### Стадия 3.1: Декомпозиция Step 05 (Test Writer)
- [ ] **Step 05.1: Test Story / Scenario Writer (`REASONING_HEAVY`)**:
  - Анализ `SPEC.md` и формирование BDD/Data-Driven матрицы тестовых сценариев.
  - Генерация файла `docs/features/{feature}/TEST_STORIES.md` с чекбоксами `[ ]`.
- [ ] **Step 05.2: Test Body Coder (`STRICT_CODEGEN` / `FAST_EXECUTION`)**:
  - Узконаправленный вызов агента с минимальным контекстом (1–5 сценариев из `TEST_STORIES.md` + сигнатура проверяемого класса + базовые фикстуры).
  - Написание атомарного кода метода теста и замена чекбокса на `[x]`.

### Стадия 3.2: Базовые фикстуры и параллелизация
- [ ] **Генерация Base Fixtures**: Создание базовых тестовых классов (`*TestBase` / helpers) до запуска генерации тел тестов.
- [ ] **Параллельное/Пакетное выполнение**: Запуск независимых субагентов Test Body Coder для параллельной генерации методов без сбоев контекста.
- [ ] **Синхронизатор файлов**: Исключение гонки условий (Race Conditions) при записи кода в один и тот же тестовый класс.

---

## 🔵 Фаза 4: Детерминированная оптимизация (Hooks, Rules & Skills)
> **Статус**: В плане  
> **Цель**: Переход от текстовых просьб в промптах к программно гарантированным проверкам и урезанию контекста.

### Стадия 4.1: Автоматические перехватчики (Hooks)
- [x] **Pre-Agent Hook**: Проверка наличия всех входных артефактов и валидация готовности окружения (JDK 21, Docker, Git, Gradle) до запуска LLM (`python-runtime/preflight_check.py` и `sdlc-orchestrator`).
- [x] **Post-Agent Hook**: Автоматический запуск сборки и верификации через `gate_runner.py` сразу после генерации шага. Сбор телеметрии вызовов инструментов и событий через `profiler_hook.py` (PostToolUse / PostInvocation).
- [x] **Guardrail Hook**: Программная блокировка несанкционированных изменений (Write Scope в `tool_shell.py`, запрет прямого коммита в защищенные ветки в `git_engine.py`, сверка с Pre-Approved Command Allowlist в `.agents/hooks.json`).
- [x] **Model Switching Advisory (In-IDE)**: Автоматическая генерация оповещения-рекомендации пользователю о переключении модели перед стартом шага на основе профиля из `.ai/agents-config.md`.
- [x] **Context Isolation Audit & Tracing**: Явная фиксация каждого этапа и состава изоляции контекста (загруженная роль, проверенные входные артефакты, границы Write Scope) в `PROFILING_SUMMARY.md`, `EXECUTION_TRACE.json` и логах оркестратора.

### Стадия 4.2: Инструменты агентов (Skills / Tools)
- [ ] **Skill `ast_structure_extractor`**: Извлечение только сигнатур публичных методов и аннотаций классов для урезания передаваемого кода на 90%.
- [ ] **Skill `db_schema_inspector`**: Прямой запрос к DDL и выполнение `EXPLAIN ANALYZE` из локального Docker-контейнера.
- [ ] **Skill `openapi_diff_checker`**: Сравнение фактического Spring REST Контроллера с контрактом в `SPEC.md`.

### Стадия 4.3: Библиотека промышленных стандартов и слоевая архитектура (Layer Meta-Standards & Technology Slots)
> **Статус**: Выполнено (`[x]`)  
> **Результат**: Модульная архитектура инженерных стандартов `.ai/guidelines/<layer>/` (9 слоёв: `architecture`, `languages`, `testing`, `persistence`, `infrastructure`, `environment`, `documentation`, `messaging`, `caching`), декомпозиция на универсальные мета-стандарты (`META.md`), технологические слоты реализации (`slots/<tech>.md`) и руководства по расширению (`EXTENSION_GUIDE.md`).

- [x] **СУБД и персистентность**:
  - [x] `persistence/slots/postgresql.md`: Схемы, search_path, partial indexes (`WHERE`), Flyway миграции, последовательности (Sequence allocationSize).
  - [x] `persistence/slots/h2.md`: In-memory H2 для быстрых изолированных срезовых тестов.
  - [ ] `persistence/slots/mysql.md`: Особенности не-транзакционного DDL, кодировка `utf8mb4`, автоинкременты.
- [x] **Кэширование и In-Memory хранилища**:
  - [x] `caching/slots/redis.md`: Стратегии Cache-Aside / Write-Through, строковые ключи, TTL, продление TTL (touch), изоляция неймспейсов.
- [x] **Брокеры сообщений и очереди**:
  - [x] `messaging/slots/kafka.md`: Топики, consumer groups, idempotence, Schema Registry, retry-топики.
  - [ ] `messaging/slots/rabbitmq.md`: AMQP Exchanges, Queues, DLX, ack/nack.
- [x] **Фреймворки и языки**:
  - [x] `architecture/slots/quarkus.md`: Quarkus 3.x, fast-jar, Panache, Flyway, RESTEasy Reactive, SmallRye OpenAPI.
  - [x] `languages/slots/java.md`: Java 21 LTS, записи (records), pattern matching, var.
  - [x] `languages/slots/kotlin.md`: Kotlin standards.
  - [x] `languages/slots/python.md`: Python standards.
  - [x] `languages/slots/typescript.md`: TypeScript standards.
  - [ ] `architecture/slots/spring-boot.md`: Spring Boot 3.x, Spring Data, Actuator, Profiles, Security, Testcontainers.
  - [ ] `architecture/slots/fastapi.md`: Python 3.11+, Pydantic v2, lifespan, dependency injection, async I/O.
- [x] **Тестирование и валидация**:
  - [x] `testing/slots/java.md`: JUnit 5, RestAssured, Mockito, обязательная behaviour verification, Anti-Deadlock моки, `checkNoStubs` гейт.
  - [x] `testing/slots/kotlin.md`, `python.md`, `typescript.md`.
  - [ ] `testing/slots/wiremock.md`: Эмуляция внешних HTTP API и mock-серверы.
- [x] **Инфраструктура и среда выполнения**:
  - [x] `infrastructure/slots/docker-compose.md`: Изоляция проектов (`-p`), детерминированные имена контейнеров, монтирование томов, флаги принудительного пересоздания `--force-recreate --build`.
  - [x] `environment/slots/local-dev.md`: Локальный манифест `docs/PROJECT_ENV.md`.
  - [x] `documentation/slots/feature-summary.md`: Структура `SUMMARY.md` с AI usage restriction notice.
- [ ] **Полнотекстовый поиск**:
  - `search-analytics/elasticsearch.md`: Маппинги, стемминг/анализаторы, bool queries, агрегации, векторный kNN поиск, ILM.

---

## 🟣 Фаза 5: Расширенные пайплайны и обработка исключений (Advanced SDLC)
> **Статус**: В плане  
> **Цель**: Адаптация системы под специфические задачи разработки и обработка тупиковых сценариев.

### Стадия 5.1: Специализированные пайплайны
- [ ] **`BUGFIX_PIPELINE.md`**: Локализация дефекта → воспроизведение падающим тестом → минимальный фикс → регрессионная проверка.
- [ ] **`REFACTORING_PIPELINE.md`**: Изменение структуры кода с условием 100% сохранения "зеленых" тестов.
- [ ] **`MIGRATION_PIPELINE.md`**: Обновление версий библиотек, фреймворков и миграций БД.

### Стадия 5.2: Human-in-the-Loop & Fallback
- [ ] **Fallback на ручной режим**: При исчерпании лимита попыток самоисправления (например, 3 неудачных прогона тестов) оркестратор останавливается, сохраняет контекст и выводит интерактивное меню:
  - Перейти в IDE для ручной правки.
  - Перезапустить шаг с измененным промптом/моделью.
  - Пропустить шаг (Skip).

---

## ⚪ Фаза 6: Полиглотная корпоративная экосистема (Polyglot Enterprise, Cloud Native & Security)
> **Статус**: В бэклоге  
> **Цель**: Масштабирование фреймворка на любые корпоративные языки программирования, распределённую аналитику, Kubernetes, API-шлюзы, безопасность и сквозной APM.

### Стадия 6.1: Высокопроизводительные и мультистековые языки & фреймворки
- [ ] **Rust**: `languages/rust.md` (Cargo workspaces, clippy, Tokio async, strict borrow checker) и `frameworks/axum.md` / `actix-web.md` (Tonic gRPC, sqlx).
- [ ] **Go**: `languages/go.md` (Go modules, `context.Context`, горутины/каналы, `golangci-lint`) и `frameworks/gin.md` / `fiber.md` (GORM/sqlc, graceful shutdown).
- [ ] **TypeScript / Node.js / Bun**: `languages/typescript.md` (strict mode, ESM) и `frameworks/nestjs.md` / `fastify.md` (Prisma, TypeORM, Vitest).
- [ ] **C# / .NET**: `languages/csharp.md` (.NET 8/9, async/await, nullable reference types) и `frameworks/aspnet-core.md` (Minimal APIs, Entity Framework Core).

### Стадия 6.2: NoSQL, Аналитика и специализированные хранилища
- [ ] **ClickHouse**: `search-analytics/clickhouse.md` (Колоночная СУБД, MergeTree движки, партиционирование, сжатие, пакетные вставки, time-series аналитика).
- [ ] **Документные и колоночные NoSQL**: `databases-nosql/mongodb.md` (Aggregation Pipelines, индексы), `databases-nosql/cassandra.md` (ScyllaDB, partition keys, Quorum).
- [ ] **Графовые и встраиваемые СУБД**: `databases-nosql/neo4j.md` (Cypher, графовые модели), `databases-relational/sqlite.md` (WAL-режим, edge-сервисы).

### Стадия 6.3: Инфраструктура, оркестрация и API-шлюзы
- [ ] **Kubernetes & Cloud Native**: `infrastructure/kubernetes.md` (Deployments, StatefulSets, Services, Ingress, Probes, Resource limits/requests, HPA) и `infrastructure/helm.md` (Helm-чарты, Kustomize-оверлеи).
- [ ] **API Gateways & Reverse Proxies**: `api-gateways/envoy.md`, `api-gateways/kong.md`, `api-gateways/traefik.md`, `api-gateways/nginx.md` (маршрутизация, rate-limiting, CORS, SSL-терминация).
- [ ] **Infrastructure as Code (IaC)**: `infrastructure/terraform.md` (Terraform / OpenTofu декларативные модули и линтинг).
- [ ] **Cloud-Native Messaging**: `messaging-queues/nats.md` (NATS JetStream, микросекундная латентность, pub/sub) и `messaging-queues/redis-streams.md`.

### Стадия 6.4: Безопасность, Auth, Secrets & Observability
- [ ] **Аутентификация и Identity**: `security-auth/keycloak.md` (IAM, OpenID Connect / OAuth2, SSO, управление realm/ролями), `security-auth/jwt.md` (PASETO, валидация на шлюзе), mTLS.
- [ ] **Управление секретами**: `security-auth/vault.md` (HashiCorp Vault, динамические секреты для БД, PKI-генерация сертификатов).
- [ ] **Распределённая наблюдаемость (APM)**: `observability/opentelemetry.md` (W3C TraceContext, OTel Collector), `observability/prometheus-grafana.md`, `observability/jaeger-tempo.md`, `observability/loki.md`.
- [ ] **Непрерывное профилирование**: `observability/pyroscope.md` (Run-time CPU & memory continuous profiling).
- [ ] **Нагрузочное тестирование**: `testing/k6.md` (сценарии k6, нагрузочные профили, SLO/SLA гейты).

---

## 📌 Стандарты и правила проекта (обязательно к соблюдению)
- **Subscription-First & Native Execution**: По умолчанию оркестратор работает на нативных мощностях и подписочных токенах текущей среды выполнения (`provider = "native"`), не требуя внешних платных API-ключей. Конфигурационные файлы служат для маршрутизации ролей или осознанного перенаправления на локальные модели (Ollama, LM Studio).
- **Единый источник истины (SSOT)**: `docs/features/{feature}/PRD.md`, `docs/features/{feature}/SPEC.md`, `docs/features/{feature}/TDD_PLAN.md` и `docs/PROJECT_ENV.md`.
- **Локализация артефактов фичи (`docs/features/{feature}/`)**: Файлы бизнес-требований (`PRD.md`) и технической спецификации (`SPEC.md`) обязаны размещаться внутри изолированного каталога фичи `docs/features/{feature}/` (`docs/features/{feature}/PRD.md` и `docs/features/{feature}/SPEC.md`), а не в корневом каталоге `docs/`. В корне `docs/` хранятся только общие проектные манифесты окружения (`docs/PROJECT_ENV.md`). Это обеспечивает полную автономность и изоляцию контекста каждой фичи.
- **Docker Compose Invariant — Принудительная перезапись и очистка артефактов**: Первый и любые контрольные запуски окружения Docker Compose в рамках пайплайна (`environment-bootstrap`, `integration-test-writer`, `integration-code-writer`) обязаны выполняться с флагами принудительной пересборки и пересоздания потенциально имеющихся одноимённых артефактов (образов, контейнеров, сетей):
  ```bash
  docker compose -p {project} up -d --build --force-recreate
  ```
  с обязательной предварительной очисткой устаревших томов и сиротских контейнеров при чистом рестарте (`docker compose -p {project} down -v --remove-orphans`), исключая любые коллизии с артефактами предыдущих запусков.
- **Ограничения `SUMMARY.md`**: Документ, генерируемый шагом 09 (`.ai/rules/documenter.md`), предназначен исключительно для людей и **не** может использоваться AI-агентами как SSOT.
- **Стандарт путей тестов**: Юнит-тесты располагаются в `src/test/java/`, интеграционные тесты — в `src/integrationTest/java/`.
- **Языковой стандарт**: Документация пайплайнов и конфигурации — русский язык. Правила и инструкции ролей (`.ai/rules/*.md`) — английский язык.