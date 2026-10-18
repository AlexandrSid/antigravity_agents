# Python Runtime & Quality Gate Toolchain

Этот каталог содержит платформонезависимые скрипты проверки и автономный консольный рантайм фреймворка Anti-Gravity Agents.

---

## 1. Назначение модулей

| Модуль | Назначение |
|---|---|
| **`gate_runner.py`** | Автоматический запуск и валидация TDD Quality Gates (`COMPILE_CHECK`, `UNIT_RED_PHASE`, `UNIT_GREEN_PHASE`, `INTEGRATION_GREEN_PHASE`). Считывает команды из `docs/PROJECT_ENV.md`. |
| **`git_engine.py`** | Защита веток (`agent-feature-*`), создание снимков (checkpoints), безопасный откат при провале тестов (`rollback`) и атомарные коммиты шагов (`commit_step`). |
| **`pipeline_parser.py`** | Парсер матрицы выполнения SDLC из Markdown (SSOT) и проверка физического существования входных артефактов на диске. |
| **`tool_shell.py`** | Bounded Local Tool Shell: безопасные инструменты `read_artifact`, `apply_patch`, `complete_step` с программным контролем `Write Scope`. |
| **`trace_logger.py`** | Запись метрик времени, токенов и расходов в `docs/features/{feature}/EXECUTION_TRACE.json`. |
| **`standalone_runner.py`** | Автономный консольный раннер для CI/CD и локальной работы без IDE. |

---

## 2. Политика автономного раннера (`standalone_runner.py`): NO RUNTIME LLM PROVIDED

`standalone_runner.py` предназначен **строго для реального автономного выполнения**:
- Он **НЕ** имитирует работу, **НЕ** делает пустых коммитов и **НЕ** зависает на интерактивных заглушках `input()`.
- Если для скрипта не настроено реальное подключение к модели, он **мгновенно завершает работу с фатальной ошибкой**:
  ```text
  FATAL: NO RUNTIME LLM PROVIDED
  ```

### Как запустить автономный раннер:
1. **С локальной моделью (Ollama / LM Studio / vLLM)**:
   ```bash
   export OLLAMA_BASE_URL="http://localhost:11434/v1"
   python python-runtime/standalone_runner.py --feature user-service
   ```
   Или задайте `base_url` в файле `.ai/agent-config.local.md`.

2. **С облачным API-ключом**:
   ```bash
   export GEMINI_API_KEY="AIzaSy..."
   python python-runtime/standalone_runner.py --feature user-service
   ```

---

## 3. Использование модулей внутри IDE (Subscription-First)

Если разработка ведётся в IDE (Google Antigravity, Cursor, Windsurf) по принципу **Subscription-First** (на встроенных подписочных токенах без внешних API-ключей):
- Оркестрацией управляет нативный агент IDE (`.antigravity/orchestrator.md` или `.cursor/rules/orchestrator.md`).
- Модули `gate_runner.py` и `git_engine.py` используются агентом как детерминированные инструменты проверки:
  ```bash
  # Проверка красной фазы TDD
  python python-runtime/gate_runner.py . UNIT_RED_PHASE

  # Создание снимка ветки
  python python-runtime/git_engine.py . user-service --git-auto
  ```
