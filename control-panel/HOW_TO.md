# Anti-Gravity Agents — HOW TO / Инструкция оператора

> **Multi-Environment Orchestration Guide / Руководство оператора**  
> Инструкция по запуску и передаче конвейеров разработки между средами разработки (Google Antigravity, Cursor, Claude Code, Python-runtime).

---

## 1. Концепция двух режимов оркестрации

Фреймворк поддерживает два независимых сценария работы:

1. **In-IDE Orchestration (Основной режим разработки, Subscription-First)**:
   - Выполняется внутри AI-first IDE (Google Antigravity, Cursor, Windsurf).
   - Использует встроенные подписочные токены текущей среды без внешних платных API-ключей.
   - Оркестратор запускает роли как изолированных субагентов (Stateless Execution).
   - Отслеживает прогресс через единый артефакт `docs/features/{feature}/PIPELINE_STATE.md`.

2. **Standalone Python Engine (Автономный консольный режим для CI/CD и локальных LLM)**:
   - Расположен в каталоге `python-runtime/`.
   - Предназначен для headless-запуска в CI/CD или работы через локальную Ollama/LM Studio.
   - **Строгая политика**: если реальное подключение к LLM не сконфигурировано (через `OLLAMA_BASE_URL` или API-ключ), скрипт **немедленно падает с ошибкой `FATAL: NO RUNTIME LLM PROVIDED`** и не имитирует деятельность.

---

## 2. Запуск в Google Antigravity IDE

В Google Antigravity агент-оркестратор активируется прямо в окне чата:

1. **Команда запуска**:
   Напишите агенту в чате:
   ```text
   Запусти Greenfield-пайплайн для фичи user-service
   ```
2. **Как работает оркестратор**:
   - Активирует скилл `sdlc-orchestrator` и контракт `.antigravity/orchestrator.md`.
   - Проверяет `docs/features/user-service/PIPELINE_STATE.md`.
   - Если это первый запуск — инициализирует матрицу шагов из `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`.
   - Последовательно вызывает субагентов для каждого шага (Шаг 01 Architect ➔ Шаг 02 TDD Planner ➔ ...).
   - Прогоняет проверки через `python python-runtime/gate_runner.py . <GATE>`.
   - Создаёт коммиты и обновляет чекбоксы `[x]` в `PIPELINE_STATE.md`.

---

## 3. Запуск в Cursor

В Cursor оркестратор работает через `.cursor/rules/orchestrator.md` с возможностью выбора модели:
1. Откройте чат Cursor (`Ctrl+L` / `Cmd+L`).
2. Напишите:
   ```text
   Запусти Greenfield-пайплайн для user-service
   ```
3. Оркестратор Cursor сверяется с `.ai/agents-config.md` и рекомендует или переключает активную модель:
   - Для `REASONING_HEAVY` (Архитектура, План) ➔ Claude 3.7 Sonnet / o3-mini / DeepSeek-R1.
   - Для `STRICT_CODEGEN` (Тесты, Код) ➔ Claude 3.5 Sonnet / GPT-4o.
   - Для `FAST_EXECUTION` (Каркас) ➔ Claude 3.5 Haiku / GPT-4o-mini.
4. Прогресс синхронизируется в тот же `PIPELINE_STATE.md`.

---

## 4. Межсредовой перенос (Cross-Environment Handoff)

Если вы начали разработку в одной среде, а продолжить хотите в другой:
1. Закоммитьте текущие изменения в ветку `agent-feature-{feature}`.
2. Откройте проект во второй среде (например, перешли из Antigravity в Cursor).
3. Скажите оркестратору:
   ```text
   Продолжи фичу user-service
   ```
4. Оркестратор считывает `docs/features/user-service/PIPELINE_STATE.md`, находит первый незавершённый шаг (`[ ]` или `IN_PROGRESS`) и бесшовно продолжает работу без потери контекста.

---

## 5. Автономный консольный запуск (`python-runtime/`)

Используется для CI/CD или запуска на локальной Ollama:

```bash
# 1. Запуск с локальной Ollama
export OLLAMA_BASE_URL="http://localhost:11434/v1"
python python-runtime/standalone_runner.py --feature user-service

# 2. Сухой прогон (проверка матрицы и входов без вызова модели)
python python-runtime/standalone_runner.py --feature user-service --dry-run
```

Если переменные подключения не заданы, вызов завершится отказом:
```text
FATAL: NO RUNTIME LLM PROVIDED
```
