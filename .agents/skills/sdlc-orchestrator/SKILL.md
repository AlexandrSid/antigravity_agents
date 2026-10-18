---
name: sdlc-orchestrator
description: Universal Multi-Agent SDLC Orchestrator for Antigravity IDE. Discovers, validates, and executes any SDLC pipeline from .ai/pipelines/ (Greenfield, Bugfix, etc.), manages PIPELINE_STATE.md, enforces Write Scope and Quality Gates, and coordinates specialized subagents.
---

# Universal SDLC Orchestrator Skill

Этот скилл активируется, когда пользователь запрашивает запуск, продолжение, валидацию или сопровождение жизненного цикла разработки (SDLC) для любой фичи или микросервиса.

---

## Протокол работы оркестратора

### 1. Определение фичи и целевого пайплайна
1. **Имя фичи (`feature`)**:
   - Извлеките имя разрабатываемой фичи/модуля из запроса пользователя (например, `user-service`, `auth`, `billing`).
   - Если имя не указано, запросите его у пользователя.
2. **Выбор пайплайна (`pipeline`)**:
   - Если пользователь явно указал пайплайн (или флаг `--pipeline`) — используйте его.
   - По умолчанию для создания нового сервиса/модуля с нуля используйте:
     `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`.
   - Для багфиксов или рефакторинга автоматически выбирайте соответствующий пайплайн из `.ai/pipelines/`.

---

### 2. Проверка состояния и протокол возобновления (`PIPELINE_STATE.md`)
1. Проверьте существование `docs/features/{feature}/PIPELINE_STATE.md`:
   - **Если файла нет**:
     - Распарсите таблицу Секции 2 из активного пайплайна (SSOT).
     - Инициализируйте `docs/features/{feature}/PIPELINE_STATE.md` по шаблону [`.ai/pipelines/templates/PIPELINE_STATE_TEMPLATE.md`](file:///.ai/pipelines/templates/PIPELINE_STATE_TEMPLATE.md).
     - Выставьте статус конвейера `INITIALIZED`.
   - **Если файл уже существует**:
     - Найдите первый шаг со статусом `PENDING` (`[ ]`) или `IN_PROGRESS`.
     - Начните выполнение ровно с этого шага, не перезапуская завершённые этапы.

---

### 3. Контроль Git-ветки
1. Проверьте текущую ветку:
   - Если активна защищённая ветка (`main`, `master`, `stage*`), создайте и переключитесь на `agent-feature-{feature}`:
     ```bash
     python python-runtime/git_engine.py . {feature} --git-auto
     ```

---

### 4. Исполнение шага с изоляцией контекста
Для каждого шага конвейера:
1. Загрузите контракт роли агента из `.ai/rules/{step.role_file}`.
2. Проверьте физическое наличие всех файлов из колонки `Inputs`. Если хотя бы один вход отсутствует — остановите шаг с сообщением о нехватке артефакта.
3. Сформируйте контекст задачи:
   - Системная роль: содержимое `.ai/rules/{step.role_file}`.
   - Входные данные: строго файлы из `Inputs`.
   - Задание: создание файлов из колонки `Outputs` строго в границах разрешённого `Write Scope`.
4. Выполните шаг (или делегируйте субагенту).

---

### 5. Quality Gate, коммит и фиксация прогресса
После создания выходных файлов шага:
1. Проверьте физическое наличие файлов из `Outputs`.
2. Если у шага назначен `Gate` (не `NONE`):
   - Запустите верификацию через раннер гейтов:
     ```bash
     python python-runtime/gate_runner.py . <GATE>
     ```
   - При сбое запустите цикл самоисправления (до 3 попыток) с передачей лога ошибки.
3. При успешном прохождении гейта:
   - Создайте атомарный коммит:
     ```bash
     git add -A
     git commit -m "feat(step-{id}): {role_name} output finalized"
     ```
   - Обновите `docs/features/{feature}/PIPELINE_STATE.md`:
     - Переключите чекбокс шага в `[x] COMPLETED`.
     - Запишите Commit SHA, среду `antigravity` и время завершения.
   - Запишите событие в `docs/features/{feature}/EXECUTION_TRACE.json`.

---

### 6. Переход к следующему шагу
Переходите к следующей строке матрицы выполнения до тех пор, пока все шаги пайплайна не получат статус `[x] COMPLETED`. По завершении выведите финальную сводку.
