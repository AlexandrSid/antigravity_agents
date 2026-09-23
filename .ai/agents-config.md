# Agent System Configuration & LLM Routing Manifest

Этот манифест определяет правила выбора и переключения LLM-моделей для каждого агента системы.
При работе в полуавтоматическом режиме через IDE (Cursor, Copilot, Antigravity) оператор или оркестратор перед запуском роли должен свериться с данным документом и переключить селектор моделей в IDE на соответствующую модель.

---

## 1. ABSTRACT MODEL PROFILES (Классификация профилей задач)

| Профиль (Profile Tier) | Назначение и требуемые характеристики | Ключевые требования к LLM |
|---|---|---|
| **`REASONING_HEAVY`** | Глубокий анализ, дискавери, выявление неопределенностей, архитектурное проектирование, декомпозиция QA-планов. | Сильное системное мышление, длинный контекст, устойчивость к логическим противоречиям. |
| **`STRICT_CODEGEN`** | Генерация продуктового кода, написание юнит/интеграционных тестов strictly по спецификациям. | Высокая точность синтаксиса, строгое соблюдение типов и контрактов, отсутствие самодеятельности/галлюцинаций. |
| **`FAST_EXECUTION`** | Шаблонная генерация, сборка окружения (Gradle/Cargo/Docker/YAML), генерация DTO-заглушек и служебных промптов. | Низкая задержка (Low Latency), высокая скорость, точное соблюдение структуры JSON/Markdown. |

---

## 2. AGENT TO PROFILE BINDING (Матрица привязки агентов)

Каждый агент системы привязан к строго определенному профилю:

| Роль Агента | Файл правил | Назначаемый профиль | Обоснование выбора |
|---|---|---|---|
| **Planner** | `.ai/rules/planner.md` | `REASONING_HEAVY` | Интервью с пользователем, выявление стека, работа с неопределенностями PRD. |
| **Architect** | `.ai/rules/architect.md` | `REASONING_HEAVY` | Проектирование контрактов, схем данных и архитектуры `SPEC.md`. |
| **QA Planner** | `.ai/rules/qa-planner.md` | `REASONING_HEAVY` | Полнота декомпозиции сценариев, граничных условий и формирование `QA_PLAN.md`. |
| **Environment Bootstrap** | `.ai/rules/environment-bootstrap.md` | `FAST_EXECUTION` | Нативная генерация конфигов сборки (Gradle, Cargo, Docker, YAML, Migrations). |
| **Skeleton Writer** | `.ai/rules/skeleton-writer.md` | `FAST_EXECUTION` | Быстрая генерация DTO, интерфейсов и пустых заглушек (Секция 0). |
| **Test Writer** | `.ai/rules/test-writer.md` | `STRICT_CODEGEN` | Написание точных падающих автотестов строго по `QA_PLAN.md` (Red Phase). |
| **Code Writer** | `.ai/rules/code-writer.md` | `STRICT_CODEGEN` | Точная реализация бизнес-логики до достижения 100% «зеленых» тестов (Green Phase). |

---

## 3. MODEL SELECTION HEURISTICS & BINDING (Правила и критерии подбора моделей)

Списки моделей в таблицах ниже служат **ориентировочными примерами класса моделей**, а не жестким ограничением. IDE, оркестратор или оператор должны выбирать **наиболее актуальную и мощную модель из доступных в среде на текущий момент**, соответствующую требуемым характеристикам профиля.

### 3.1 Требования к свойствам моделей по профилям (Selection Criteria)

При выборе модели из доступного списка в вашей IDE (Cursor, GitHub Copilot, Antigravity, Ollama и др.) руководствуйтесь следующими правилами:

| Профиль | Минимальный класс / Требуемые фичи | Примеры классов моделей (Conceptual) |
|---|---|---|
| **`REASONING_HEAVY`** | Флагманские модели с CoT (Chain-of-Thought), глубоким рассуждением, большим контекстным окном (128k+) и высоким баллом в системном архитектурном анализе. | *Top-tier Reasoning Models* (e.g., O-series, Claude Sonnet/Opus, Gemini Pro, DeepSeek R1/Large). |
| **`STRICT_CODEGEN`** | Специализированные кодинг-модели или актуальные Frontier-модели с высокой точностью соблюдения строгой типографики, синтаксиса и контрактов без галлюцинаций. | *Advanced Coding Models* (e.g., Claude Sonnet, GPT-4o, Qwen-Coder-32B+, DeepSeek Coder). |
| **`FAST_EXECUTION`** | Легковесные, высокоскоростные модели с минимальной задержкой (Low Latency) и низкой стоимостью, идеально выполняющие генерацию по шаблону. | *Lightweight / Fast Models* (e.g., GPT-Mini/Micro class, Claude Haiku class, Gemini Flash, Qwen/Llama 3B-8B). |

---

### 3.2 Dynamic IDE / Local Override (`.ai/agents-config.local.md`)

Для зафиксированных локальных настроек конкретного разработчика или команды создается необязательный локальный файл `.ai/agents-config.local.md` (добавляется в `.gitignore`).

Если файл `.ai/agents-config.local.md` существует, IDE/оркестратор считывает точные имена моделей из него:

```markdown
# Local Model Overrides (Ignored by Git)

[REASONING_HEAVY]
provider = "auto" # or "copilot", "cursor", "ollama"
preferred_model = "current-best-reasoning"

[STRICT_CODEGEN]
provider = "auto"
preferred_model = "current-best-coder"

[FAST_EXECUTION]
provider = "auto"
preferred_model = "current-best-fast"
```

Если `.ai/agents-config.local.md` отсутствует, IDE автоматически сопоставляет профили `REASONING_HEAVY`, `STRICT_CODEGEN` и `FAST_EXECUTION` с лучшими имеющимися у неё в наличии моделями соответствующих категорий.

---

## 4. OPERATIONAL INSTRUCTIONS FOR IDE AGENTS
Каждый агент перед началом выполнения фазы обязан вывести в первом сообщении строку с рекомендуемым профилем и моделью, например:
> `**[qa-planner]**: Active Profile: REASONING_HEAVY (Recommended Model: Claude 3.7 Sonnet / o3-mini)`
