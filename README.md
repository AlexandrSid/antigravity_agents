# Anti-Gravity Agents — Stage 1 MVP

> **Human-readable project presentation. This README is not a Single Source of Truth (SSOT) and must not be used by AI agents as a technical reference.**  
> Authoritative requirements, specifications, and commands live in `docs/PRD.md`, `docs/features/{feature}/SPEC.md`, and `docs/PROJECT_ENV.md`.

## English

### What is Anti-Gravity Agents?

Anti-Gravity Agents is a sandbox for designing and testing a multi-agent software development system. The project explores how specialized AI agents can collaborate through explicit contracts, immutable handoffs, test gates, and clearly separated areas of responsibility.

### What makes this branch different?

The `stage1_mvp` branch preserves **Stage 1**, the first complete experiment in the project. Instead of preparing the agent system in isolation, Stage 1 created the agents from scratch while using them to build a small real application end to end.

The experiment had two connected goals:

1. Design a reusable multi-agent workflow for Greenfield software development.
2. Validate that workflow by delivering a working, testable, containerized MVP.

### What was created during Stage 1?

Stage 1 produced a sequential Greenfield SDLC pipeline with dedicated roles for:

- architecture and feature specification;
- TDD planning;
- environment bootstrapping;
- source skeleton generation;
- Unit/Slice test authoring;
- production implementation;
- Integration/E2E test authoring;
- integration defect correction;
- final human-readable documentation.

Each role has an explicit contract, model profile, write scope, handoff conditions, and Definition of Done. Project requirements, technical contracts, execution commands, and human summaries are deliberately separated.

### The validation application

The pipeline was exercised by building a compact User Service MVP using Java 21, Spring Boot 3.3.6, Gradle 8.10.2, Flyway, H2, and Docker Compose.

The resulting application provides REST endpoints for user CRUD, normalized identity lookup, shared address resolution, direct family relationships, soft deletion, RFC 7807 errors, deterministic seed data, and Docker-based execution. Unit/Slice and Docker-backed Integration/E2E suites were also introduced as separate quality gates.

The application is important as evidence of the process, but it is not the primary purpose of this repository. The main Stage 1 result is the Greenfield multi-agent development system that emerged from building and correcting the MVP.

### Where to read more

- Human-readable feature overview: `docs/features/user-service/SUMMARY.md`
- Product requirements: `docs/PRD.md`
- Authoritative feature specification: `docs/features/user-service/SPEC.md`
- Environment and commands: `docs/PROJECT_ENV.md`
- Greenfield pipeline protocol: `.ai/GREENFIELD_SDLC_PIPELINE.md`
- Agent role contracts: `.ai/rules/`

---

## Русский

> **Человекочитаемая презентация проекта. Этот README не является единым источником правды (SSOT), и AI-агентам запрещено использовать его как технический справочник.**  
> Авторитетные требования, спецификации и команды находятся в `docs/PRD.md`, `docs/features/{feature}/SPEC.md` и `docs/PROJECT_ENV.md`.

### Что такое Anti-Gravity Agents?

Anti-Gravity Agents — это песочница для проектирования и проверки мультиагентной системы разработки программного обеспечения. Проект исследует совместную работу специализированных AI-агентов через явные контракты, неизменяемые передачи результатов, тестовые контрольные этапы и строго разделённые зоны ответственности.

### Чем отличается эта ветка?

Ветка `stage1_mvp` сохраняет **Stage 1** — первый завершённый эксперимент проекта. Вместо изолированной подготовки агентной системы на этой стадии агенты создавались с нуля и сразу применялись для сквозной разработки небольшого реального приложения.

У эксперимента было две взаимосвязанные цели:

1. Спроектировать переиспользуемый мультиагентный процесс для Greenfield-разработки.
2. Проверить этот процесс выпуском работающего, тестируемого и контейнеризованного MVP.

### Что было создано в Stage 1?

В Stage 1 появился последовательный Greenfield SDLC-пайплайн с отдельными ролями для:

- архитектуры и спецификации возможности;
- TDD-планирования;
- подготовки окружения;
- генерации каркаса исходного кода;
- написания Unit/Slice-тестов;
- реализации продуктового кода;
- написания Integration/E2E-тестов;
- исправления интеграционных дефектов;
- подготовки итоговой документации для людей.

Для каждой роли определены контракт, профиль модели, область записи, условия передачи управления и критерии готовности. Требования, технические контракты, команды выполнения и человекочитаемые сводки намеренно разделены.

### Приложение для проверки процесса

Пайплайн был проверен созданием компактного MVP User Service на Java 21, Spring Boot 3.3.6, Gradle 8.10.2, Flyway, H2 и Docker Compose.

Получившееся приложение предоставляет REST API для CRUD-операций с пользователями, нормализованного поиска, переиспользования адресов, прямых семейных связей, мягкого удаления, ошибок RFC 7807, детерминированных начальных данных и запуска в Docker. Unit/Slice и Docker-backed Integration/E2E-наборы были выделены в отдельные контрольные этапы качества.

Само приложение важно как доказательство работоспособности процесса, но не является главной целью репозитория. Основной результат Stage 1 — Greenfield-система мультиагентной разработки, сформированная в ходе создания и исправления MVP.

### Где читать подробнее

- Человекочитаемый обзор возможности: `docs/features/user-service/SUMMARY.md`
- Требования продукта: `docs/PRD.md`
- Авторитетная спецификация возможности: `docs/features/user-service/SPEC.md`
- Окружение и команды: `docs/PROJECT_ENV.md`
- Протокол Greenfield-пайплайна: `.ai/GREENFIELD_SDLC_PIPELINE.md`
- Контракты ролей агентов: `.ai/rules/`
