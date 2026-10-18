# Anti-Gravity Agents — HOW TO / Инструкция оператора

> **User Control Panel Guide / Руководство пользователя панели управления**  
> Инструкции по запуску пайплайнов, описание флагов оркестратора, управление средами и сценарии разработки.

---

## 📑 Содержание / Table of Contents

- [Русский](#-русский)
  - [1. Быстрый старт (Quick Start)](#1-быстрый-старт-quick-start)
  - [2. Команда запуска и все доступные флаги CLI](#2-команда-запуска-и-все-доступные-флаги-cli)
  - [3. Примеры типовых сценариев использования](#3-примеры-типовых-сценариев-использования)
  - [4. Запуск тестов оркестратора](#4-запуск-тестов-оркестратора)
  - [5. Подписочная маршрутизация и локальные модели (Subscription-First)](#5-подписочная-маршрутизация-и-локальные-модели-subscription-first)
  - [6. Защита веток, чекпоинты Git и откаты](#6-защита-веток-чекпоинты-git-и-откаты)
  - [7. Трассировка и аудит (EXECUTION_TRACE.json)](#7-трассировка-и-аудит-execution_tracejson)
- [English](#-english)
  - [1. Quick Start](#1-quick-start)
  - [2. CLI Command and All Available Flags](#2-cli-command-and-all-available-flags)
  - [3. Common Operational Scenarios](#3-common-operational-scenarios)
  - [4. Running Orchestrator Tests](#4-running-orchestrator-tests)
  - [5. Subscription-First Routing & Local Models](#5-subscription-first-routing--local-models)
  - [6. Branch Protection, Git Checkpoints & Rollbacks](#6-branch-protection-git-checkpoints--rollbacks)
  - [7. Tracing and Auditing (EXECUTION_TRACE.json)](#7-tracing-and-auditing-execution_tracejson)

---

## 🇷🇺 Русский

### 1. Быстрый старт (Quick Start)

Все инструменты запуска собраны в директории `control-panel/`:
- **Windows (CMD / PowerShell):** `control-panel\run` (или `run` внутри папки `control-panel`)
- **Linux / macOS:** `./control-panel/run.sh`
- **Кроссплатформенный запуск через Python:** `python control-panel/run.py`

#### Интерактивный запуск (рекомендуется)
Запустите лаунчер без аргументов:
```bash
python control-panel/run.py
```
Мастер управления:
1. Предложит выбрать пайплайн из каталога `.ai/pipelines/` (по умолчанию: `[1] Greenfield SDLC`);
2. Запросит имя фичи (например, `auth`, `billing`, `user-service`);
3. Предложит выбрать режим выполнения (по умолчанию: интерактивный с подтверждением оператора);
4. Автоматически включит защиту веток Git (`--git-auto`).

#### Короткие команды быстрого запуска:
```bash
# Интерактивный запуск Greenfield-пайплайна для вашей фичи
python control-panel/run.py greenfield my-feature

# Автономный запуск (auto)
python control-panel/run.py greenfield my-feature --mode auto

# Тестовая валидация без изменений (dry-run)
python control-panel/run.py greenfield my-feature --dry-run
```

---

### 2. Команда запуска и все доступные флаги CLI

При необходимости прямого вызова низкоуровневого оркестратора:

```bash
python .ai/scripts/orchestrator.py [ПАРАМЕТРЫ]
```

#### Таблица всех флагов

| Флаг | Тип / Значения | Обязательный | Значение по умолчанию | Описание |
|---|---|:---:|---|---|
| `--feature` | `STRING` | **Да** | — | Название фичи / компонента. Подставляется вместо плейсхолдера `{feature}` в путях артефактов и именах веток (например, `docs/features/{feature}/SPEC.md`). |
| `--pipeline` | `PATH` | Нет | `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md` | Путь к Markdown-файлу пайплайна (таблице SSOT). Может быть как относительным от корня репозитория, так и абсолютным. |
| `--mode` | `auto` \| `interactive` | Нет | `auto` | Режим выполнения шагов: <br>• `auto`: сквозное безостановочное выполнение шагов пайплайна;<br>• `interactive`: запрос подтверждения оператора (`[Y/n/q]`) перед каждым шагом. |
| `--git-auto` | *Флаг-переключатель* | Нет | `False` | Автоматически создает и переключается на изолированную ветку `agent-feature-{feature}`, если текущая ветка защищена (`main`, `master`, `prod`, `release/*`). |
| `--from-step` | `INTEGER` (например, `5`) | Нет | `None` (с 1 шага) | Возобновить выполнение с шага номер N (1-индексация). Удобно после ручной правки упавшего шага. |
| `--dry-run` | *Флаг-переключатель* | Нет | `False` | Режим валидации (сухой прогон). Парсит пайплайн, проверяет зависимости артефактов, определяет среду и выводит план выполнения **без вызовов LLM, без записи файлов и без Git-операций**. |
| `-h`, `--help` | *Флаг-переключатель* | Нет | — | Выводит справочную информацию по аргументам и завершает работу. |

---

### 3. Примеры типовых сценариев использования

#### Сценарий А: Безопасная валидация пайплайна (Dry Run)
```bash
python control-panel/run.py greenfield auth-service --dry-run
```

#### Сценарий Б: Пошаговый запуск под контролем оператора (Interactive)
```bash
python control-panel/run.py greenfield billing --mode interactive
```

#### Сценарий В: Автономный запуск (Unattended Auto)
```bash
python control-panel/run.py greenfield user-profile --mode auto
```

#### Сценарий Г: Возобновление после ручной доработки (Resume from Step)
```bash
python control-panel/run.py greenfield user-service --from-step 5
```

---

### 4. Запуск тестов оркестратора

Все тесты ядра оркестратора выполняются штатными средствами Python:

```bash
# Через встроенный модуль unittest:
python -m unittest discover .ai/scripts/tests/ -v

# Или через pytest (если установлен):
pytest .ai/scripts/tests/ -v
```

---

### 5. Подписочная маршрутизация и локальные модели (Subscription-First)

1. **Принцип Subscription-First:**  
   По умолчанию оркестратор не требует платных ключей API (OpenAI / Anthropic). Он автоматически определяет рабочую среду:
   - Наличие папки `.antigravity/` → адаптер Google Antigravity IDE (нативные подписочные токены);
   - Наличие `.cursor/` → адаптер Cursor;
   - Наличие `.claude/` → адаптер Claude Code;
   - Наличие `.windsurf/` → адаптер Windsurf;
   - Иначе → `generic` среда.

2. **Подключение локальных моделей (Ollama / vLLM / LM Studio):**  
   Для запуска отдельных ролей (например, генератора кода) на локальной машине создайте локальный конфигурационный файл `.ai/agent-config.local.md` (он внесен в `.gitignore`):
   ```toml
   [STRICT_CODEGEN]
   provider = "ollama"
   preferred_model = "qwen2.5-coder:7b"
   ```
   Оркестратор бесшовно объединит его с базовым `.ai/agents-config.md`.

---

### 6. Защита веток, чекпоинты Git и откаты

- **Защита веток:** оркестратор запрещает выполнение на ветках `main`, `master`, `prod`, `release/*` без флага `--git-auto`.
- **Чекпоинт перед шагом:** перед началом каждого шага создается Git-метка чекпоинта `agent-checkpoint-step-{N}` или сохраняется рабочий stash.
- **Откат (Rollback):** если агент нарушает Write Scope или не проходит Quality Gate, оркестратор **автоматически откатывает рабочее дерево** до чекпоинта шага.

---

### 7. Трассировка и аудит (EXECUTION_TRACE.json)

После каждого шага и по завершении пайплайна обновляется структурированный отчет:
- `docs/features/{feature}/EXECUTION_TRACE.json` — содержит длительность каждого шага, статусы проверок Quality Gates, количество вызовов инструментов, затронутые файлы и учет стоимости/токенов по Price Registry.

---

## 🇬🇧 English

### 1. Quick Start

All execution shortcuts and operator tools are organized inside `control-panel/`:
- **Windows (CMD / PowerShell):** `control-panel\run` (or `run` inside `control-panel/`)
- **Linux / macOS:** `./control-panel/run.sh`
- **Cross-platform Python command:** `python control-panel/run.py`

#### Interactive Wizard (Recommended)
Run without arguments to launch the step-by-step setup wizard:
```bash
python control-panel/run.py
```
The wizard will:
1. Prompt you to select a pipeline from `.ai/pipelines/` (currently: `[1] Greenfield SDLC`);
2. Ask for the feature name (e.g., `auth`, `billing`, `user-service`);
3. Prompt for the execution mode (defaults to operator-guided interactive mode);
4. Automatically activate git branch protection (`--git-auto`).

#### Shorthand Launch Commands:
```bash
# Launch Greenfield pipeline interactively with automatic feature branching
python control-panel/run.py greenfield my-feature

# Unattended autonomous mode
python control-panel/run.py greenfield my-feature --mode auto

# Dry run validation
python control-panel/run.py greenfield my-feature --dry-run
```

---

### 2. CLI Command and All Available Flags

For direct orchestrator execution:

```bash
python .ai/scripts/orchestrator.py [OPTIONS]
```

#### Complete Flags Reference

| Flag | Type / Values | Required | Default | Description |
|---|---|:---:|---|---|
| `--feature` | `STRING` | **Yes** | — | Name of the feature or component. Substitutes the `{feature}` placeholder in pipeline artifact paths (e.g. `docs/features/{feature}/SPEC.md`). |
| `--pipeline` | `PATH` | No | `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md` | Path to the pipeline Markdown specification file (SSOT table). Accepts relative or absolute filesystem paths. |
| `--mode` | `auto` \| `interactive` | No | `auto` | Execution mode: <br>• `auto`: runs pipeline steps end-to-end without stopping;<br>• `interactive`: prompts operator for confirmation (`[Y/n/q]`) before executing each step. |
| `--git-auto` | *Flag / switch* | No | `False` | Automatically creates and switches to an isolated `agent-feature-{feature}` branch if currently on a protected branch (`main`, `master`, `prod`, `release/*`). |
| `--from-step` | `INTEGER` (e.g. `5`) | No | `None` (starts at step 1) | Resume pipeline execution starting at step N (1-indexed). |
| `--dry-run` | *Flag / switch* | No | `False` | Dry run validation mode. Parses the pipeline, checks input artifact readiness, resolves platform configs, and displays the plan **without LLM calls, file edits, or git modifications**. |
| `-h`, `--help` | *Flag / switch* | No | — | Displays the command-line help message with examples and exits. |

---

### 3. Common Operational Scenarios

#### Scenario A: Safe Pipeline Dry Run
```bash
python control-panel/run.py greenfield auth-service --dry-run
```

#### Scenario B: Interactive Operator-Guided Execution
```bash
python control-panel/run.py greenfield billing --mode interactive
```

#### Scenario C: Unattended Autonomous Execution
```bash
python control-panel/run.py greenfield user-profile --mode auto
```

#### Scenario D: Resuming from a Specific Step
```bash
python control-panel/run.py greenfield user-service --from-step 5
```

---

### 4. Running Orchestrator Tests

```bash
# Using Python's built-in unittest:
python -m unittest discover .ai/scripts/tests/ -v

# Or using pytest:
pytest .ai/scripts/tests/ -v
```

---

### 5. Subscription-First Routing & Local Models

1. **Subscription-First Principle:**  
   The framework requires zero paid external API keys by default. It automatically detects the host environment:
   - `.antigravity/` folder present → Google Antigravity IDE adapter (native subscription tokens);
   - `.cursor/` folder present → Cursor IDE adapter;
   - `.claude/` folder present → Claude Code adapter;
   - `.windsurf/` folder present → Windsurf IDE adapter;
   - Otherwise → `generic` environment.

2. **Local Model Routing (Ollama / vLLM / LM Studio):**  
   To route specific agent profiles to local models, create a gitignored override file at `.ai/agent-config.local.md`:
   ```toml
   [STRICT_CODEGEN]
   provider = "ollama"
   preferred_model = "qwen2.5-coder:7b"
   ```
   The resolver merges this local file with the base `.ai/agents-config.md`.

---

### 6. Branch Protection, Git Checkpoints & Rollbacks

- **Branch Protection:** execution is blocked on `main`, `master`, `prod`, `release/*` unless `--git-auto` is provided.
- **Step Checkpoints:** a git checkpoint (`agent-checkpoint-step-{N}`) or stash is created before each step begins.
- **Scope Enforcement & Automatic Rollbacks:** if an agent writes outside its designated write scope or fails a Quality Gate, the orchestrator rolls back the working tree to the step checkpoint.

---

### 7. Tracing and Auditing (EXECUTION_TRACE.json)

During and after execution, the orchestrator produces and updates:
- `docs/features/{feature}/EXECUTION_TRACE.json` — recording execution duration, quality gate status, tool invocations, modified files, and token/cost estimates via the built-in Price Registry.
