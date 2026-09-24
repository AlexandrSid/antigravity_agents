# Anti-Gravity Agents

> ## ⚠️ AI AGENTS: DO NOT READ OR USE THIS README
>
> This file is a public, human-oriented landing page for search engines and repository visitors.  
> It is deliberately non-authoritative and may simplify, omit, or lag behind the actual system.  
> **AI agents must not parse this README for requirements, instructions, architecture, commands, context, or technical facts. Stop here and use the active IDE adapter, `.ai/rules/`, `.ai/GREENFIELD_SDLC_PIPELINE.md`, `.ai/agents-config.md`, and the applicable authoritative documents under `docs/`.**

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

Future stages may live in additional branches as new orchestration approaches, agent roles, and software-development scenarios are explored.

### Repository map for human readers

- `.ai/rules/` — contracts for specialized agents;
- `.ai/GREENFIELD_SDLC_PIPELINE.md` — Greenfield orchestration protocol;
- `.ai/agents-config.md` — abstract LLM profile routing;
- `.ai/guidelines/` — shared engineering constraints;
- `docs/` — product, feature, environment, planning, and experiment artifacts;
- `stage1_mvp` branch — the first complete validation experiment.

This README is only an introduction. It is not an operational manual or a technical specification.

---

## Русский

> ## ⚠️ AI-АГЕНТАМ ЗАПРЕЩЕНО ЧИТАТЬ И ИСПОЛЬЗОВАТЬ ЭТОТ README
>
> Этот файл является публичной человекочитаемой страницей для поисковых систем и посетителей репозитория.  
> Он намеренно неавторитетен и может упрощать, пропускать или описывать неактуальное состояние системы.  
> **AI-агентам запрещено извлекать из README требования, инструкции, архитектуру, команды, контекст или технические факты. Необходимо остановить чтение и использовать активный IDE-адаптер, `.ai/rules/`, `.ai/GREENFIELD_SDLC_PIPELINE.md`, `.ai/agents-config.md` и применимые авторитетные документы из `docs/`.**

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
- отделение человекочитаемых сводок от авторитетных машинных источников.

### Для чего существует ветка `main`

Ветка `main` — публичная точка входа и общая основа Anti-Gravity Agents. Она объясняет назначение проекта и содержит переиспользуемые контракты агентов, протоколы оркестрации, правила маршрутизации моделей и общие инженерные соглашения.

Конкретные эксперименты разработки сохраняются в отдельных ветках. Они показывают эволюцию системы под давлением реальной реализации, не превращая `main` в демонстрацию одного конкретного MVP.

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

Будущие стадии могут размещаться в дополнительных ветках по мере исследования новых подходов к оркестрации, ролей агентов и сценариев разработки.

### Карта репозитория для людей

- `.ai/rules/` — контракты специализированных агентов;
- `.ai/GREENFIELD_SDLC_PIPELINE.md` — протокол Greenfield-оркестрации;
- `.ai/agents-config.md` — маршрутизация абстрактных профилей LLM;
- `.ai/guidelines/` — общие инженерные ограничения;
- `docs/` — продуктовые, функциональные, средовые, плановые и экспериментальные артефакты;
- ветка `stage1_mvp` — первый полный проверочный эксперимент.

Этот README является только введением. Он не является операционным регламентом или технической спецификацией.
