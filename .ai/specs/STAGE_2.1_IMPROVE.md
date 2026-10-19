# Спецификация улучшений Фазы 2.1: Стандарты, Изоляция Docker, Observability и Устранение Friction (STAGE-2.1-IMPROVE)

> **Статус**: Утверждено к реализации  
> **Базовый документ**: [.ai/specs/STAGE_2_SPEC.md](file:///.ai/specs/STAGE_2_SPEC.md)  
> **Целевая ветка**: `stage_2_improvements`  
> **Основание**: Ретроспектива полного сквозного прогона Greenfield SDLC (`user-service`)

---

## 1. Ретроспектива пилотного запуска Greenfield (`user-service`)

### 1.1. Достигнутые результаты
1. **Сквозной проход конвейера**: Мультиагентная система успешно выполнила шаги 01–09 (от системного анализа архитектора до финальной документации).
2. **Качественный технологический стек**: Сгенерирован полноценный микросервис на Kotlin 2.0 / JVM 21, Quarkus 3.x (Quarkus REST, Panache Kotlin, Flyway), H2 TCP Server и Docker Compose.
3. **Пройденные Quality Gates**: Успешно отработали фазы `COMPILE_CHECK`, `UNIT_RED_PHASE` (проверка падения тестов по `AssertionError`), `UNIT_GREEN_PHASE` и интеграционные тесты с реальной базой данных.
4. **Фиксация артефактов**: Создан полный набор проектных документов (`SPEC.md`, `TDD_PLAN.md`, `PROJECT_ENV.md`, `INTEGRATION_REPORT.md`, `SUMMARY.md`, `PIPELINE_STATE.md`).

### 1.2. Критические замечания и узкие места процесса
В процессе выполнения выявлены 4 фундаментальные проблемы, снижающие автономность и надежность фреймворка:

1. **Отсутствие отслеживания токенов в IDE (Token Tracking Gap)**:
   - В автономном Python-рантайме (`standalone_runner.py`) токены считываются напрямую из API-ответов провайдеров.
   - В среде IDE (Antigravity) агенты работали без явной фиксации объема контекста и сожженных токенов шага, из-за чего `EXECUTION_TRACE.json` не отражал реальные затраты.
2. **Лавина ручных подтверждений команд (Approval Fatigue)**:
   - Пользователю пришлось вручную подтверждать более 35 терминальных команд (`git`, `gradle`, `docker`, `python`, `powershell`).
   - Причина: отсутствие доверенного списка команд в проекте и хаотичные «исследовательские» команды агентов (`Get-ChildItem`, поиск `gradle.bat`, проверка версий), когда окружение оказывалось не настроенным заранее.
3. **Попытки использования inline Python-скриптов в PowerShell**:
   - Агенты пытались передавать сложный код через `python -c "..."` в терминал PowerShell, что гарантированно приводило к сбоям синтаксического парсера Windows PowerShell из-за конфликтов одинарных/двойных кавычек и спецсимволов `$`.
4. **Конфликты деплоймент-артефактов и отсутствие изоляции в Docker**:
   - При повторных прогонах поднимались устаревшие образы (`stale images`), переиспользовались разделяемые volumes с несовместимой схемой данных, возникали коллизии портов и имен контейнеров.
   - **Главный инвариант**: Docker не должен получать один и тот же артефакт от разных пайплайнов или разных прогонов.

---

### 1.3. Каталог 11 технических инцидентов и их корневой анализ

| № | Инцидент | Корневая причина | Каким решением устраняется системно |
|---|---|---|---|
| **1** | Сбои `gate_runner.py` на Windows | POSIX-допущения в коде (вызовы `./gradlew` без расширения `.bat`, прямые слэши в путях). | Кроссплатформенная нормализация путей и расширений исполняемых файлов в кодовой базе рантайма. |
| **2** | Отсутствие Gradle в системном PATH | Ожидание глобального `gradle`, тогда как проект должен опираться на wrapper. | Стандарт: проект обязан содержать Gradle Wrapper (`gradlew.bat` / `gradlew`), генерируемый на шаге `environment-bootstrap`. |
| **3** | Несовпадение версии системной Java (нужен JDK 21) | Системный PATH указывал на устаревший JDK; переменная `JAVA_HOME` не была выставлена в сессии. | Введение Pre-flight валидации с точным экспортом `$env:JAVA_HOME` и fail-fast проверкой до запуска генерации. |
| **4** | Неподдерживаемый синтаксис частичных индексов в H2 | `engineering-standards.md` требовал partial unique index `WHERE is_deleted = false`, не поддерживаемый H2 2.x. | Декомпозиция стандартов: вынос ограничений СУБД в технологический профиль (`h2.md`), перенос контроля уникальности в сервис. |
| **5** | Сбой компиляции Java-алиасов (`CREATE ALIAS`) в H2 | Попытка генерации данных через in-memory dynamic Java-функцию H2, где `_` в Java 21 стал ключевым словом. | Стандарт: запрет любых процедурных динамических функций языка хоста в миграциях; использование строго ANSI SQL DML (`INSERT INTO`). |
| **6** | Конфликт Sequence в H2 при ручной вставке ID | Вставка фиксированных ID (1..1000) не инкрементирует генератор `IDENTITY` таблицы. | Стандарт сидирования: при ручной вставке ID скрипт обязан завершаться сбросом генератора (`RESTART WITH {max_id + 1}`). |
| **7** | Ошибки экранирования PowerShell при `python -c` | Попытка передавать многострочный Python-код через аргументы командной строки в PowerShell. | Жесткий запрет в правилах агентов на inline `python -c`. Все вспомогательные скрипты сохраняются на диск как `.py` файлы. |
| **8** | Устаревший Docker-образ сервиса (Stale Image) | Запуск `docker compose up` без флага сборки `--build` и без версионирования тегов. | Стандарт версионирования: динамические теги образов и обязательный флаг `--build` при запуске тестов. |
| **9** | Запрет удаленного создания БД в TCP H2 | Контейнер `oscarfonts/h2` отклоняет запросы на создание баз без флага `-ifNotExists`. | Технологический профиль `stacks/databases/h2.md`: обязательное наличие `-ifNotExists` в параметрах `H2_OPTIONS`. |
| **10** | Несовпадение диалекта Hibernate ORM в Quarkus | Quarkus 3.15 ожидает H2 >= 2.3.230, тогда как сервер запускался на версии 2.1.214. | Профиль `stacks/frameworks/quarkus.md`: указание `quarkus.datasource.db-version` при работе с H2 < 2.3. |
| **11** | Гонка готовности контейнеров (Race Condition) | Приложение стартовало до открытия порта 9092 сервером базы данных. | Стандарт Compose: все зависимые сервисы обязаны иметь `healthcheck`, а приложение — `condition: service_healthy`. |

---

## 2. Новая архитектура стандартов: Декомпозиция Core vs Stack Standards

### 2.1. Принцип разделения
Стандарты категорически запрещено загрязнять особенностями конкретных языков, СУБД или библиотек:
1. **Core Standards (`.ai/guidelines/core/`)**: Платформонезависимые фундаментальные инженерные инварианты, обязательные для любого проекта на любом языке (Java, Rust, Go, Python, TypeScript).
2. **Tech Stack Standards (`.ai/guidelines/stacks/`)**: Модульные технологические профили, сгруппированные по категориям и описывающие особенности, ограничения и синтаксис конкретной технологии.

```text
.ai/guidelines/
├── core/                                   # ТЕХНОЛОГИЧЕСКИ НЕЙТРАЛЬНЫЕ СТАНДАРТЫ
│   ├── architecture-standards.md           # 3-tier архитектура, границы DTO/Entity, RFC 7807, семантика Soft Delete
│   ├── deployment-artifacts.md             # Версионирование деплой-артефактов, Compose-изоляция, чистые тома
│   ├── database-migrations.md              # Чистый декларативный SQL, запрет рантайм-кода, инвариант сброса sequence
│   ├── environment-preflight.md            # Контракт декларации окружения, fail-fast валидация, allowlist команд
│   └── pipeline-state-standards.md         # Протокол возобновления (Resume Protocol), чекпоинты, матрица состояний
│
└── stacks/                                 # СПЕЦИФИЧНЫЕ ТЕХНОЛОГИЧЕСКИЕ ПРОФИЛИ
    ├── languages/
    │   ├── kotlin.md                       # Kotlin 2.0, JVM 21, Gradle Kotlin DSL, null-safety
    │   ├── java.md                         # Java 21, ключевые слова, sourceCompatibility, Maven/Gradle
    │   ├── rust.md                         # Cargo, clippy, toolchain
    │   └── python.md                       # Python 3.11+, venv, pyproject.toml
    │
    ├── frameworks/
    │   ├── quarkus.md                      # Quarkus 3.x, Fast-JAR, Panache Kotlin, dialect versioning
    │   ├── spring-boot.md                  # Spring Boot 3.x, Profiles, Actuator
    │   └── fastapi.md                      # FastAPI, Pydantic v2, uvicorn
    │
    ├── databases/
    │   ├── h2.md                           # Синтаксис RESTART WITH, отсутствие partial index, TCP флаг -ifNotExists
    │   ├── postgresql.md                   # Поддержка partial indexes, схемы, search_path, setval
    │   └── mysql.md                        # Особенности автоинкрементов, транзакционность DDL
    │
    └── infrastructure/
        ├── docker-compose.md               # COMPOSE_PROJECT_NAME, healthchecks, depends_on conditions
        └── kubernetes.md                   # Probes, resources, configmaps
```

---

### 2.2. Протокол динамического подключения (Dynamic Discovery Protocol)
1. **Шаг 01 (Architect)** анализирует раздел 1.1 в `docs/PRD.md` и определяет целевой стек.
2. В создаваемом документе `docs/features/{feature}/SPEC.md` Архитектор формирует обязательную секцию:
   ```markdown
   ## Applicable Technical Standards
   - **Core Standards**: `.ai/guidelines/core/*.md`
   - **Technology Profiles**:
     - `.ai/guidelines/stacks/languages/kotlin.md`
     - `.ai/guidelines/stacks/frameworks/quarkus.md`
     - `.ai/guidelines/stacks/databases/h2.md`
     - `.ai/guidelines/stacks/infrastructure/docker-compose.md`
   ```
3. **Субагенты последующих шагов** (`tdd-planner`, `environment-bootstrap`, `code-writer`, `integration-test-writer`):
   - Загружают в свой контекст исключительно те стандарты, которые указаны в `SPEC.md`.
   - Агент, реализующий сервис на Go или Rust, никогда не получит в контекст правила для Quarkus или H2.

---

## 3. Стандарт версионирования и изоляции деплоймент-артефактов

Зафиксирован в Core-стандарте `deployment-artifacts.md` как жесткий инвариант: **Docker не должен получать один и тот же неверсионированный артефакт от разных пайплайнов или повторных запусков**.

### 3.1. Требования к именованию и тегированию
1. **Запрет `:latest`**: Использование тега `:latest` для проектных сервисов категорически запрещено.
2. **Динамический тег образа**:
   `IMAGE_TAG=${BUILD_VERSION:-0.1.0}-${FEATURE_NAME}` или `{service-name}:${COMMIT_SHA_SHORT}`.
3. **Изоляция проектов Docker Compose**:
   - Каждый пайплайн обязан задавать уникальное имя проекта Compose:
     `COMPOSE_PROJECT_NAME=${FEATURE_NAME}-${RUN_ID:-local}`.
   - Запрещен глобальный хардкод `container_name: ...` без префикса проекта.

### 3.2. Изоляция хранилищ и жизненный цикл
1. **Эфемерные тома (Clean Volume Contract)**:
   - Все тома баз данных обязаны быть изолированы префиксом проекта Compose.
   - Запуск интеграционного тестирования обязан начинаться с очистки:
     ```bash
     docker compose -p ${COMPOSE_PROJECT_NAME} down -v --remove-orphans
     ```
2. **Обязательная принудительная пересборка**:
   ```bash
   docker compose -p ${COMPOSE_PROJECT_NAME} up -d --build --force-recreate
   ```
3. **Контракт готовности (Healthcheck Contract)**:
   - Любой вспомогательный контейнер (БД, брокер сообщений) обязан определять секцию `healthcheck`.
   - Основное приложение обязано зависеть от него с условием:
     ```yaml
     depends_on:
       db:
         condition: service_healthy
     ```

---

## 4. Решение проблемы частых подтверждений команд (Approval Fatigue)

### 4.1. Pre-flight Validation Hook (Шаг 00)
Вместо хаотичных попыток агентов исследовать файловую систему и переменные среды в процессе работы, внедряется детерминированный инструмент предварительной проверки: `python-runtime/preflight_check.py`.

- **Что проверяет**:
  - Наличие и версию Java (`java -version == 21`, валидность `$env:JAVA_HOME`).
  - Доступность Git в текущей рабочей директории.
  - Наличие сборщика проекта / обертки (`gradlew.bat` / `gradlew`).
  - Доступность запущенного демона Docker (`docker info`).
- **Поведение Fail-Fast**:
  - Если среда не готова, пайплайн **немедленно останавливается до вызова первого агента** с понятным человеку выводом:
    ```text
    [PRE-FLIGHT FAILED]: 
    - Требуется JDK 21. Обнаружен: JDK 17 по пути C:\Program Files\Java\jdk-17
    Действие: выполните в терминале перед запуском:
      $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
      $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    ```
  - Это полностью исключает 15 исследовательских команд агента по диску!

### 4.2. Манифест разрешенных команд (`Pre-Approved Command Allowlist`)
В `docs/PROJECT_ENV.md` вводится обязательная секция `## Pre-Approved Command Allowlist`. Архитектор на Шаге 01 фиксирует полный перечень команд, которые понадобятся на всех шагах конвейера:
```markdown
## Pre-Approved Command Allowlist
- `.\gradlew.bat compileKotlin`
- `.\gradlew.bat test`
- `.\gradlew.bat test --tests *`
- `docker compose -p user-service-local up -d --build --force-recreate`
- `docker compose -p user-service-local down -v --remove-orphans`
- `git add -A`
- `git commit -m *`
```

### 4.3. Авто-одобрение через `PreToolUse` Hook
В `.agents/hooks.json` регистрируется системный хук Antigravity IDE:
- При каждом вызове тула `run_command` хук сверяет исполняемую строку с Allowlist из `PROJECT_ENV.md`.
- Если команда совпадает с разрешенным шаблоном — хук автоматически возвращает в stdout IDE:
  ```json
  {
    "decision": "allow",
    "reason": "Command matches project pre-approved allowlist in docs/PROJECT_ENV.md"
  }
  ```
- Человеку больше не требуется нажимать подтверждение на штатные команды компиляции, тестов и сборки Docker!
- Если агент пытается выполнить неизвестную команду — хук возвращает `{"decision": "ask"}`, защищая систему от несанкционированных действий.

---

## 5. Система профилирования, аудита и трекинга токенов (Observability)

Пользователь должен иметь прозрачную картину работы фреймворка: какой агент запущен, на какой модели, сколько токенов передано, какие хуки и тулы сработали.

### 5.1. Архитектура хуков IDE (`.agents/hooks.json`)
Фреймворк подключает 5 стандартных событий жизненного цикла:

```json
{
  "framework-profiler": {
    "enabled": true,
    "PreInvocation": [
      {
        "type": "command",
        "command": "python python-runtime/profiler_hook.py --event PreInvocation",
        "timeout": 15
      }
    ],
    "PostInvocation": [
      {
        "type": "command",
        "command": "python python-runtime/profiler_hook.py --event PostInvocation",
        "timeout": 15
      }
    ],
    "PreToolUse": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "python python-runtime/profiler_hook.py --event PreToolUse",
            "timeout": 15
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "python python-runtime/profiler_hook.py --event PostToolUse",
            "timeout": 15
          }
        ]
      }
    ],
    "Stop": [
      {
        "type": "command",
        "command": "python python-runtime/profiler_hook.py --event Stop",
        "timeout": 15
      }
    ]
  }
}
```

### 5.2. Обработчик телеметрии `python-runtime/profiler_hook.py`
Скрипт принимает системный JSON от IDE на `stdin` и выполняет:
1. **Идентификация контекста**: Извлекает `modelName`, `conversationId`, `stepIdx`. Сверяет с текущим активным шагом из `docs/features/**/PIPELINE_STATE.md`.
2. **Оценка объема токенов (Token Estimator)**:
   - Рассчитывает суммарный размер входных файлов текущего шага и системного промпта роли (в символах / ~3.5 символа на токен).
   - Фиксирует метрику в реальном времени.
3. **Логирование событий**: Добавляет запись в `docs/features/{feature}/PROFILING_LOG.jsonl`.
4. **Обновление сводки**: Перегенерирует наглядную сводку `docs/features/{feature}/PROFILING_SUMMARY.md`.
5. **Gating доверенных команд**: Для события `PreToolUse` сверяет команду с `Pre-Approved Command Allowlist` и выдает решение `allow` или `ask`.

### 5.3. Артефакты профилирования

#### А. Детальный лог: `docs/features/{feature}/PROFILING_LOG.jsonl`
```json
{"timestamp": "2026-10-19T10:45:12Z", "event": "SUBAGENT_START", "step": "06", "role": "code-writer", "model": "claude-3-7-sonnet", "input_chars": 14250, "est_tokens": 3850}
{"timestamp": "2026-10-19T10:45:15Z", "event": "HOOK_PRE_TOOL", "step": "06", "tool": "write_to_file", "target": "src/main/kotlin/UserService.kt"}
{"timestamp": "2026-10-19T10:45:20Z", "event": "HOOK_PRE_TOOL", "step": "06", "tool": "run_command", "cmd": ".\\gradlew.bat compileKotlin", "decision": "allow"}
{"timestamp": "2026-10-19T10:45:28Z", "event": "HOOK_POST_TOOL", "step": "06", "tool": "run_command", "exit_code": 0, "duration_sec": 8.1}
{"timestamp": "2026-10-19T10:45:30Z", "event": "SUBAGENT_FINISH", "step": "06", "role": "code-writer", "status": "SUCCESS", "duration_sec": 18.2, "output_chars": 6400}
```

#### Б. Сводная таблица: `docs/features/{feature}/PROFILING_SUMMARY.md`

| Шаг | Роль субагента | Назначенная модель | Входной контекст | Вызовы тулов | Длительность | Статус |
|:---:|---|---|:---:|:---:|:---:|:---:|
| **01** | `architect` | `gemini-2.5-pro` | ~8.2k токенов | `write_to_file` (2) | 24.3s | `SUCCESS` |
| **02** | `tdd-planner` | `claude-3-7-sonnet` | ~12.5k токенов | `write_to_file` (1) | 31.0s | `SUCCESS` |
| **03** | `environment-bootstrap` | `gemini-2.5-flash` | ~5.1k токенов | `write_to_file` (4), `run_command` (1) | 12.8s | `SUCCESS` |
| **04** | `skeleton-writer` | `gemini-2.5-flash` | ~7.3k токенов | `write_to_file` (3), `run_command` (1) | 15.4s | `SUCCESS` |
| **05** | `test-writer` | `claude-3-7-sonnet` | ~14.1k токенов | `write_to_file` (2), `run_command` (2) | 38.2s | `SUCCESS` |
| **06** | `code-writer` | `claude-3-7-sonnet` | ~16.8k токенов | `write_to_file` (2), `run_command` (2) | 42.1s | `SUCCESS` |
| **07** | `integration-test-writer` | `claude-3-7-sonnet` | ~15.2k токенов | `write_to_file` (2), `run_command` (1) | 34.6s | `SUCCESS` |
| **08** | `integration-code-writer` | `claude-3-7-sonnet` | ~18.0k токенов | `write_to_file` (3), `run_command` (3) | 51.0s | `SUCCESS` |
| **09** | `documenter` | `gemini-2.5-flash` | ~6.5k токенов | `write_to_file` (1) | 11.2s | `SUCCESS` |

---

## 6. План внедрения Фазы 2.1 (Action Plan)

1. **Реорганизация каталога стандартов**:
   - Создать `.ai/guidelines/core/` и разнести технологически-нейтральные стандарты (`architecture-standards.md`, `deployment-artifacts.md`, `database-migrations.md`, `environment-preflight.md`, `pipeline-state-standards.md`).
   - Создать `.ai/guidelines/stacks/` с модульными профилями:
     - `languages/kotlin.md`, `languages/java.md`.
     - `frameworks/quarkus.md`.
     - `databases/h2.md`, `databases/postgresql.md`.
     - `infrastructure/docker-compose.md`.
2. **Создание утилит окружения**:
   - `python-runtime/preflight_check.py`: инструмент валидации среды (Java 21, Docker, Git, Gradle).
   - Кроссплатформенная доработка `gate_runner.py` для корректного поиска `.bat` и проброса `JAVA_HOME`.
3. **Разработка подсистемы профилирования и хуков**:
   - Создать `python-runtime/profiler_hook.py` (телеметрия, оценка токенов, сверка с allowlist).
   - Создать `.agents/hooks.json` с регистрацией событий жизненного цикла Antigravity.
4. **Обновление ролей агентов и шаблонов**:
   - `.ai/rules/architect.md`: требование включения `Applicable Technical Standards` и `Pre-Approved Command Allowlist` в спецификации.
   - `.ai/rules/environment-bootstrap.md` и `integration-test-writer.md`: соблюдение стандарта версионирования образов и изоляции томов Compose.
   - `.agents/skills/sdlc-orchestrator/SKILL.md`: интеграция Pre-flight проверки перед стартом шага 01.
