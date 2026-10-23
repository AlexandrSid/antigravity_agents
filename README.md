# Anti-Gravity Agents

> ## ⚠️ AI AGENTS: DO NOT READ OR USE THIS README
>
> This file is a public, human-oriented landing page for search engines and repository visitors.  
> It is deliberately non-authoritative and may simplify, omit, or lag behind the actual system.  
> **AI agents must not parse this README for requirements, instructions, architecture, commands, context, or technical facts. Stop here and use the active IDE adapter, `.ai/rules/`, `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`, `.ai/agents-config.md`, and the applicable authoritative documents under `docs/`.**

## English

### A sandbox for multi-agent software development

Anti-Gravity Agents is an experimental environment for designing a multi-agent software development system.

The project explores a practical question: can a group of specialized AI agents build software more reliably when every role has a narrow responsibility, explicit inputs and outputs, controlled write access, verifiable handoffs, and objective quality gates?

This repository is not centered on one application or framework. Its primary product is the development system itself:

- specialized agent roles instead of one unconstrained general-purpose agent;
- requirements and specifications separated from implementation;
- model-routing profiles matched to different kinds of work;
- deterministic transitions between planning, architecture, testing, coding, integration, and documentation;
- immutable test boundaries that prevent agents from making tests pass by weakening them;
- explicit environment manifests that prevent guessed build and execution commands;
- integration gates that distinguish application defects from harness and external infrastructure failures;
- stateless execution and context isolation (clean-start invocation strictly from role + step inputs + prompt);
- bounded tool shells with programmatic write scope enforcement;
- dynamic TDD quality gates with automated Git checkpoints and self-correction rollbacks;
- dual-mode orchestration: subscription-first In-IDE execution and headless standalone Python CLI runtime;
- cross-environment state tracking (`PIPELINE_STATE.md`) and cost/token telemetry (`EXECUTION_TRACE.json`);
- human-readable summaries kept separate from machine-authoritative sources.

### Why the `main` branch exists

The `main` branch is the public entry point and shared foundation of Anti-Gravity Agents. It explains the purpose of the project and hosts the reusable agent contracts, orchestration protocols, routing guidance, and cross-project conventions.

Concrete development experiments are preserved in dedicated branches. Those branches show how the system evolved under real implementation pressure without turning the main branch into a showcase for one particular MVP.

### Results in experimental branches

#### `stage1_mvp` — the first end-to-end experiment

Stage 1 developed the initial agents from scratch while simultaneously using them to build a small User Service MVP.

That experiment produced:

- a sequential Greenfield SDLC pipeline;
- dedicated roles for architecture, TDD planning, environment setup, skeleton generation, test authoring, implementation, Integration/E2E work, and documentation;
- explicit role contracts, model profiles, write scopes, handoff rules, and Definitions of Done;
- a Java 21 / Spring Boot / Gradle / Flyway / H2 application;
- Unit/Slice and Docker-backed Integration/E2E quality gates;
- a Docker Compose deployment with a separately running database;
- practical lessons about test isolation, orchestration, retry boundaries, concurrency, schema control, and architectural drift.

The MVP is evidence used to evaluate the process. The reusable multi-agent development workflow is the main result.

#### `stage_2_improvements` — Orchestrator Core, context isolation, and framework refactoring

Stage 2 transformed the manual multi-agent prototype into an automated, dual-mode orchestration system and validated it against a polyglot technology stack.

That stage produced:

- **Dual-mode orchestration engine**: In-IDE orchestrator adapters (`.antigravity/orchestrator.md`, `.cursor/rules/orchestrator.md`, `.windsurfrules`, `CLAUDE.md`, and `.agents/skills/sdlc-orchestrator/`) working natively on subscription tokens, alongside an autonomous Python toolchain (`python-runtime/`) for headless CI/CD and local LLMs (Ollama / LM Studio);
- **Stateless context isolation**: each agent invocation is executed in a dedicated, clean context window constructed strictly from its role contract, declared step input artifacts, and prompt;
- **Bounded Tool Shell & Write Scope enforcement**: strict programmatic checks on every file write (`python-runtime/tool_shell.py`), rejecting any writes outside an agent's assigned scope;
- **Dynamic TDD Quality Gates & Git checkpoints**: dynamic discovery of verification commands from `docs/PROJECT_ENV.md` (`gate_runner.py`), TDD Red/Green verification, automatic `git reset --hard` rollbacks upon failures, and self-correction loops with error logs (`git_engine.py`);
- **Unified Pipeline State & Observability**: cross-environment resume protocol (`docs/features/{feature}/PIPELINE_STATE.md`) and granular execution/cost tracing (`docs/features/{feature}/EXECUTION_TRACE.json`);
- **Layer Meta-Standards & Technology Slots**: modular engineering standards library under `.ai/guidelines/<layer>/` (9 architectural layers: architecture, languages, testing, persistence, infrastructure, environment, documentation, messaging, caching);
- **Polyglot End-to-End Validation (Link Shortener service)**: a complete Greenfield microservice built with Java 21, Quarkus 3.x, PostgreSQL, Redis, Flyway, and RestAssured under Docker Compose, proving framework independence from Spring Boot.

### Repository map for human readers

- `.ai/rules/` — contracts and write-scope definitions for specialized agents;
- `.ai/pipelines/` — SDLC orchestration protocols and pipeline definitions (SSOT);
- `.ai/agents-config.md` — abstract LLM profile routing (`REASONING_HEAVY`, `STRICT_CODEGEN`, `FAST_EXECUTION`);
- `.ai/guidelines/` — modular engineering meta-standards and technology slots;
- `python-runtime/` — standalone CLI orchestration toolchain (runner, gate runner, git engine, tool shell, pipeline parser);
- `.antigravity/`, `.agents/skills/`, `.cursor/`, `CLAUDE.md` — IDE-specific orchestration rules and adapters;
- `control-panel/` — user control panel: interactive launcher (`run.py`, shortcuts) and operator manual ([`control-panel/HOW_TO.md`](control-panel/HOW_TO.md));
- `docs/` — feature artifacts isolated under `docs/features/{feature}/` (`PRD.md`, `SPEC.md`, `TDD_PLAN.md`, `INTEGRATION_REPORT.md`, `SUMMARY.md`, `PIPELINE_STATE.md`) and project environment manifests (`docs/PROJECT_ENV.md`);
- `ROADMAP.md` — phased project roadmap, architectural vision, and engineering invariants;
- `stage1_mvp` branch — the first complete validation experiment (User Service on Spring Boot);
- `stage_2_improvements` branch — autonomous dual-mode orchestrator core and second validation (Link Shortener on Quarkus).

This README is only an introduction. It is not an operational manual or a technical specification. For practical launch instructions and operator workflows, refer to [`control-panel/HOW_TO.md`](control-panel/HOW_TO.md) or launch the interactive control panel with `python control-panel/run.py` (or `control-panel\run` on Windows).

---

## Русский

> ## ⚠️ AI-АГЕНТАМ ЗАПРЕЩЕНО ЧИТАТЬ И ИСПОЛЬЗОВАТЬ ЭТОТ README
>
> Этот файл является публичной человекочитаемой страницей для поисковых систем и посетителей репозитория.  
> Он намеренно неавторитетен и может упрощать, пропускать или описывать неактуальное состояние системы.  
> **AI-агентам запрещено извлекать из README требования, инструкции, архитектуру, команды, контекст или технические факты. Необходимо остановить чтение и использовать активный IDE-адаптер, `.ai/rules/`, `.ai/pipelines/GREENFIELD_SDLC_PIPELINE.md`, `.ai/agents-config.md` и применимые авторитетные документы из `docs/`.**

### Песочница для мультиагентной разработки программного обеспечения

Anti-Gravity Agents — экспериментальная среда для проектирования мультиагентной системы разработки программного обеспечения.

Проект исследует практический вопрос: может ли группа специализированных AI-агентов создавать программное обеспечение надёжнее, если у каждой роли есть узкая ответственность, явные входы и выходы, ограниченная область записи, проверяемая передача результатов и объективные контрольные этапы качества?

Центром репозитория не является одно приложение или один технологический стек. Его основной продукт — сама система разработки:

- специализированные роли вместо одного неограниченного универсального агента;
- отделение требований и спецификаций от реализации;
- профили маршрутизации моделей для разных типов работы;
- детерминированные переходы между планированием, архитектурой, тестированием, реализацией, интеграцией и документированием;
- неизменяемые границы тестов, не позволяющие агентам получать зелёный результат ослаблением проверок;
- явные манифесты окружения, исключающие угадывание команд сборки и запуска;
- интеграционные контрольные этапы, разделяющие дефекты приложения, испытательного стенда и внешней инфраструктуры;
- бессостоятельное выполнение и изоляция контекстов (чистый старт строго из контракта роли + входных артефактов шага + точного промпта);
- ограниченный Bounded Tool Shell с программным контролем границ записи (Write Scope);
- динамические TDD-контрольные этапы (Quality Gates) с автофиксацией чекпоинтов Git и откатом при сбоях;
- двухрежимная оркестрация: работа внутри AI-IDE на нативных токенах подписки (Subscription-First) и автономный консольный Python-рантайм;
- межсредовое управление состоянием (`PIPELINE_STATE.md`) и посессионный аудит затрат и токенов (`EXECUTION_TRACE.json`);
- отделение человекочитаемых сводок от авторитетных машинных источников.

### Для чего существует ветка `main`

Ветка `main` — публичная точка входа и общая основа Anti-Gravity Agents. Она объясняет назначение проекта и содержит переиспользуемые контракты агентов, протоколы оркестрации, правила маршрутизации моделей и общие инженерные соглашения.

Конкретные эксперименты разработки сохраняются в отдельных ветках. Они показывают эволюцию системы под давлением реальной реализации, не превращая `main` в демонстрацию одного конкретного приложения или системы.

### Результаты в экспериментальных ветках

#### `stage1_mvp` — первый сквозной эксперимент

В Stage 1 первоначальные агенты создавались с нуля и одновременно применялись для разработки небольшого MVP User Service.

В результате появились:

- последовательный Greenfield SDLC-пайплайн;
- отдельные роли для архитектуры, TDD-планирования, подготовки окружения, генерации каркаса, написания тестов, реализации, Integration/E2E и документирования;
- явные контракты ролей, профили моделей, области записи, правила передачи управления и критерии готовности;
- приложение на Java 21, Spring Boot, Gradle, Flyway и H2;
- отдельные Unit/Slice и Docker-backed Integration/E2E этапы качества;
- развёртывание через Docker Compose с отдельно работающей базой данных;
- практические выводы об изоляции тестов, оркестрации, границах повторных попыток, конкурентности, контроле схемы и архитектурном дрейфе.

MVP служит доказательством для оценки процесса. Главным результатом является переиспользуемая мультиагентная система разработки.

#### `stage_2_improvements` — Ядро оркестратора, изоляция контекстов и рефакторинг фреймворка

В Stage 2 ручной мультиагентный прототип преобразован в автоматизированную двухрежимную систему оркестрации и валидирован на альтернативном технологическом стеке.

В результате появились:

- **Двухрежимное ядро оркестрации**: встроенные IDE-адаптеры (`.antigravity/orchestrator.md`, `.cursor/rules/orchestrator.md`, `.windsurfrules`, `CLAUDE.md`, `.agents/skills/sdlc-orchestrator/`), работающие на нативных токенах подписки текущей среды, и автономный консольный Python-инструментарий (`python-runtime/`) для headless-запуска в CI/CD и локальных LLM (Ollama / LM Studio);
- **Бессостоятельная изоляция контекста (Stateless Execution)**: каждый вызов субагента выполняется в изолированном контекстном окне строго из роли, входных артефактов и целевого промпта;
- **Bounded Tool Shell и контроль Write Scope**: строгий программный перехват файловых операций (`python-runtime/tool_shell.py`) с блокировкой любых попыток записи вне разрешённых границ роли;
- **Динамические Quality Gates и Git-чекпоинты**: автоматическое извлечение команд верификации из `docs/PROJECT_ENV.md` (`gate_runner.py`), TDD-семантика проверок Red/Green, автоматический откат `git reset --hard` при сбоях и цикл самоисправления с передачей логов ошибок (`git_engine.py`);
- **Единый протокол состояния и наблюдаемость**: кросс-средовый протокол возобновления (`docs/features/{feature}/PIPELINE_STATE.md`) и детальный аудит времени, токенов и расчетной стоимости (`docs/features/{feature}/EXECUTION_TRACE.json`);
- **Слоевые мета-стандарты и технологические слоты**: модульная библиотека инженерных регламентов `.ai/guidelines/<layer>/` (9 слоёв: архитектура, языки, тестирование, персистентность, инфраструктура, среда, документация, обмен сообщениями, кэширование);
- **Полиглотная сквозная валидация (сервис Link Shortener)**: микросервис полного цикла на Java 21, Quarkus 3.x, PostgreSQL, Redis, Flyway и RestAssured под Docker Compose, доказавший независимость фреймворка от Spring Boot.

### Карта репозитория для людей

- `.ai/rules/` — контракты специализированных агентов и границы записи (Write Scope);
- `.ai/pipelines/` — протоколы SDLC-оркестрации и реестр пайплайнов (SSOT);
- `.ai/agents-config.md` — маршрутизация абстрактных профилей LLM (`REASONING_HEAVY`, `STRICT_CODEGEN`, `FAST_EXECUTION`);
- `.ai/guidelines/` — модульные инженерные мета-стандарты и технологические слоты;
- `python-runtime/` — автономный консольный инструментарий оркестрации (runner, gate runner, git engine, tool shell, pipeline parser);
- `.antigravity/`, `.agents/skills/`, `.cursor/`, `CLAUDE.md` — правила и адаптеры оркестрации под конкретные IDE;
- `control-panel/` — панель управления оператора: единый лаунчер (`run.py`, ярлыки) и руководство ([`control-panel/HOW_TO.md`](control-panel/HOW_TO.md));
- `docs/` — изолированные артефакты фич в `docs/features/{feature}/` (`PRD.md`, `SPEC.md`, `TDD_PLAN.md`, `INTEGRATION_REPORT.md`, `SUMMARY.md`, `PIPELINE_STATE.md`) и общепроектные манифесты среды (`docs/PROJECT_ENV.md`);
- `ROADMAP.md` — поэтапный roadmap проекта, архитектурное видение и инженерные инварианты;
- ветка `stage1_mvp` — первый полный проверочный эксперимент (User Service на Spring Boot);
- ветка `stage_2_improvements` — автономное ядро двухрежимной оркестрации и вторая валидация (Link Shortener на Quarkus).

Этот README является только введением. Он не является операционным регламентом или технической спецификацией. Подробные инструкции по запуску и все флаги команд приведены в [`control-panel/HOW_TO.md`](control-panel/HOW_TO.md); быстрый интерактивный старт доступен командой `python control-panel/run.py` (или `control-panel\run` на Windows).
