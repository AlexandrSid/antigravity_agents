# Спецификация реализации Фазы 2: Orchestrator Core (STAGE-2-SPEC)

> **Статус**: Выполнено (`[x]`)  
> **Целевая ветка**: `stage_2_improvements`  
> **Базовый стандарт**: Zero External Dependencies (Python 3 Standard Library) & Subscription-First  
> **Фактический статус**: Ядро рантайма полностью реализовано в `python-runtime/`, валидировано кроссплатформенно (Windows/POSIX) и верифицировано в сквозном Greenfield SDLC конвейере (`user-service`).

---

## 1. Фундаментальные принципы архитектуры

### 1.1. Subscription-First & Native Execution
1. Оркестратор по умолчанию использует нативные подписочные токены и встроенные модели текущей среды разработки (`provider = "native"`), в которой запущен процесс (Google Antigravity, Cursor, Claude Code).
2. Запрещены любые обязательные платные внешние API-ключи (OpenAI, Anthropic) для базового функционирования фреймворка.
3. Сторонние провайдеры или локальные инференс-серверы (Ollama, LM Studio) подключаются исключительно осознанно через конфигурационные переопределения (`.ai/agent-config.local.md`) для оффлайн-работы или оптимизации затрат.
4. Взаимодействие с LLM в режиме `native` выполняется программно на основе платформенных адаптеров сред, исключая ручное копирование промптов между окнами.

### 1.2. Изоляция платформ и чистый агностический Core
1. **Чистое ядро (`.ai/`)**: Содержит исключительно платформо-независимые артефакты:
   - Пайплайны жизненного цикла (`.ai/GREENFIELD_SDLC_PIPELINE.md` и будущие пайплайны).
   - Общесистемные роли агентов (`.ai/rules/*.md`).
   - Манифест маршрутизации моделей (`.ai/agents-config.md`).
   - Скрипты оркестратора (`.ai/scripts/*.py`).
2. **Платформенные адаптеры (`.antigravity/`, `.cursor/`, и др.)**:
   - Корневые скрытые директории сред содержат специфичные правила, маппинги команд и адаптеры вызова моделей конкретной IDE.
   - Оркестратор динамически определяет активную среду и использует соответствующий адаптер.

### 1.3. Zero External Dependencies
Кодовая база CLI-оркестратора реализуется строго на стандартной библиотеке Python 3 (`urllib.request`, `json`, `pathlib`, `subprocess`, `re`, `argparse`, `time`, `shutil`). Никаких внешних зависимостей из PyPI (`pip install`) не требуется.

---

## 2. Изоляция контекста и Bounded Local Tool Shell (Стадия 2.1)

### 2.1. Stateless Execution (Архитектура чистого старта)
Каждый шаг конвейера запускается с полностью очищенным контекстом. Контекстный срез для агента формируется оркестратором strictly из 3 составляющих:
1. **Системная роль**: Содержимое `.ai/rules/{agent}.md`.
2. **Входные артефакты**: Содержимое файлов, строго перечисленных в колонке `Inputs` для данного шага в активном пайплайне (например, `docs/PRD.md`, `docs/features/{feature}/SPEC.md`).
3. **Точный промпт-задание**: Формируемое оркестратором задание на выполнение шага с указанием ожидаемых артефактов и требований.

### 2.2. Bounded Local Tool Shell
Оркестратор управляет сессией агента через детерминированный набор инструментов (через JSON-Schema Tool Calling или текстовый DSL-протокол):
- `read_artifact(path: str) -> str`: Чтение содержимого файла в пределах проекта.
- `apply_patch(path: str, diff_or_content: str) -> str`: Запись или модификация файла на диске.
- `complete_step(summary_message: str) -> void`: Сигнал агента об успешном завершении своей работы на шаге.

### 2.3. Write Scope Enforcement & Bounded Iterations
1. **Контроль границ записи**: При каждом вызове `apply_patch` оркестратор считывает раздел `Write Scope` из `.ai/rules/{agent}.md`. Если путь целевого файла выходит за пределы разрешённого Scope — вызов блокируется с ошибкой:
   `Permission denied: File '{path}' is outside Write Scope for role '{agent}'`.
2. **Ограничение ReAct-цикла**: Лимит максимум 5 итераций вызова инструментов на один шаг. Превышение лимита трактуется как зацикливание агента, вызывая аварийную остановку шага и запуск протокола отката.

---

## 3. Динамический парсинг пайплайна и манифестов (Стадии 2.2 и 2.3)

### 3.1. SSOT: Прямой парсер Markdown-пайплайна
- Источником истины о шагах конвейера является напрямую файл `.ai/GREENFIELD_SDLC_PIPELINE.md`. Создание дублирующих конфигураций в JSON/YAML запрещено.
- Оркестратор парсит Markdown-таблицу матрицы SDLC (секция 2), валидируя схему колонок:
  `Step`, `Agent Role`, `Inputs`, `Outputs`, `Model Profile`, `Gate`, `Responsibility`.

### 3.2. Манифест маршрутизации (`agents-config.md`)
- Оркестратор парсит TOML-подобные секции профилей (`[REASONING_HEAVY]`, `[STRICT_CODEGEN]`, `[FAST_EXECUTION]`) из `.ai/agents-config.md`.
- Поддерживается приоритетное переопределение через `.ai/agent-config.local.md` (игнорируемый Git).
- Параметры секций: `provider`, `preferred_model`, `fallback_model`, `temperature`, `max_output_tokens`, `base_url`.

### 3.3. CLI Интерфейс и флаги
Стандарт запуска оркестратора:
```bash
python .ai/scripts/orchestrator.py \
  --pipeline .ai/GREENFIELD_SDLC_PIPELINE.md \
  --feature <feature_name> \
  --mode <auto|interactive> \
  [--git-auto]
```
- `--pipeline`: Путь к сценарию пайплайна (по умолчанию `.ai/GREENFIELD_SDLC_PIPELINE.md`).
- `--feature`: Обязательный идентификатор фичи (интерполирует пути `docs/features/{feature}/*`).
- `--mode`: `auto` (сквозной прогон) или `interactive` (остановка перед каждым шагом с запросом подтверждения).
- `--git-auto`: Автоматическое создание и переключение на ветку `agent-feature-{feature}`.

---

## 4. Quality Gates, TDD Validation и Git Checkpoints (Стадия 2.4)

### 4.1. Динамический резолвинг команд
Команды верификации считываются исключительно из секции `Execution & Verification Commands` в `docs/PROJECT_ENV.md`.
Маппинг колонок `Gate`:
- `NONE` (Шаги 01, 02, 03, 07, 09): Без автоматических проверок сборки.
- `COMPILE_CHECK` (Шаг 04): Вызов команды `Compile / Build Check` (exit code = 0).
- `UNIT_RED_PHASE` (Шаг 05): Вызов `Unit / Slice Test`:
  - Компиляция обязана быть успешной (exit code компилятора = 0).
  - Тесты обязаны упасть исключительно по причине `AssertionError` или `UnsupportedOperationException`. Падение инфраструктуры тестов или ошибка компиляции считаются сбоем шага.
- `UNIT_GREEN_PHASE` (Шаг 06): Вызов `Unit / Slice Test` (exit code = 0, 100% тестов пройдены).
- `INTEGRATION_GREEN_PHASE` (Шаг 08): Вызов `Integration / E2E Test` (exit code = 0).

### 4.2. Git Checkpoints & Self-Correction Loop
1. **Снимок (Checkpoint)**: Перед запуском шага оркестратор сохраняет текущий Commit SHA.
2. **Обработка отказа**:
   - При провале Quality Gate выполняется сброс: `git reset --hard <checkpoint_sha>`.
   - Запускается повторная попытка (Retry) с передачей исходного контекстного среза + лога ошибки (стектрейс компиляции или упавшие ассерты).
   - Лимит повторных попыток: **3 попытки**.
3. **Human-in-the-Loop Fallback**:
   Если после 3 попыток шаг не пройден, оркестратор выполняет `git reset --hard <checkpoint_sha>` и переходит в режим паузы с меню:
   - `[1] Retry manually in IDE (Pause & Wait)`
   - `[2] Force retry with elevated reasoning model`
   - `[3] Abort pipeline execution`

### 4.3. Branch Protection
- Оркестратор проверяет имя текущей Git-ветки.
- Запрещена прямая запись в ветки `main`, `master`, `develop`, `stage*`.
- Если включен `--git-auto`, оркестратор автоматически создает и переключается на ветку `agent-feature-{feature}`.
- Если `--git-auto` выключен, скрипт прерывает выполнение с указанием необходимой команды переключения.
- По завершении каждого успешного шага оркестратор выполняет атомарный Git-коммит:
  `feat(step-{id}): {agent_name} output finalized`.

---

## 5. Observability & Cost Tracking (`EXECUTION_TRACE.json`)

Оркестратор фиксирует каждый вызов в `docs/features/{feature}/EXECUTION_TRACE.json`.
Формат атомарной записи:
```json
{
  "step_id": "04",
  "agent_role": "skeleton-writer",
  "model_used": "native-environment-model",
  "provider": "native",
  "timestamp_start": "2026-10-18T20:10:00Z",
  "timestamp_end": "2026-10-18T20:10:05Z",
  "duration_sec": 5.12,
  "tokens": {
    "prompt_tokens": 1420,
    "completion_tokens": 580,
    "total_tokens": 2000
  },
  "cost_usd": 0.0,
  "exit_status": "SUCCESS"
}
```
- Для `provider = "native"` и локальных моделей (Ollama) `cost_usd = 0.0`.
- Для облачных API стоимость рассчитывается по встроенному Price Registry за 1M токенов.

---

## 6. Модульная структура скриптов оркестратора (`python-runtime/`)

- [x] **`pipeline_parser.py`** — Прямой парсинг Markdown таблицы конвейера (`.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`), валидация схемы колонок (`Step`, `Agent Role`, `Inputs`, `Outputs`, `Model Profile`, `Gate`).
- [x] **`config_resolver.py`** — Чтение профилей моделей из `agents-config.md`, локальных оверрайдов `agent-config.local.md` и детекция платформ (.antigravity, .cursor, .windsurf).
- [x] **`tool_shell.py`** — Bounded Local Tool Shell (`read_artifact`, `apply_patch`, `complete_step`), валидация границ `Write Scope` по ролям агентов и жесткий лимит 5 итераций на шаг.
- [x] **`gate_runner.py`** — Динамический резолвинг команд верификации из `PROJECT_ENV.md`, кроссплатформенный запуск (.bat / .sh) и валидация Quality Gates (`COMPILE_CHECK`, `UNIT_RED_PHASE`, `UNIT_GREEN_PHASE`, `INTEGRATION_GREEN_PHASE`).
- [x] **`git_engine.py`** — Защита веток (`agent-feature-*`, `agent-bugfix-*`), создание контрольных точек (Checkpoints), автоматический откат (`git reset --hard`) при падении гейтов и атомарные коммиты успешных шагов.
- [x] **`trace_logger.py`** — Запись `EXECUTION_TRACE.json`, расчет расхода токенов и стоимости по встроенному реестру цен (Price Registry).
- [x] **`standalone_runner.py`** — Главная CLI-точка входа (`--pipeline`, `--feature`, `--mode auto|interactive`, `--git-auto`), цикл шагов и Human-in-the-Loop Fallback меню.
- [x] **`preflight_check.py`** — Pre-flight проверка зависимостей окружения (JDK 21, Docker, Git, Gradle) с fail-fast рекомендациями.
- [x] **`profiler_hook.py`** — Интеграция с хуками IDE Antigravity (`.agents/hooks.json`), автоодобрение allowlist команд и сбор метрик в реальном времени.
- [x] **In-IDE Orchestrators** — Универсальные правила и скилл оркестрации для интерактивных сессий (`.agents/skills/sdlc-orchestrator/SKILL.md`, `.antigravity/orchestrator.md`, `.cursor/rules/orchestrator.md`).
