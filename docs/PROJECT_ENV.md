# Local Project Environment & Commands

## Service Metadata
- **Service Identifier**: `link-shortener`
- **Tech Stack**: Java 21 · Quarkus 3.15.x · Gradle 8.10 · PostgreSQL 16 · Redis 7 · Docker + Docker Compose · Flyway · Sqids

---

## Toolchain Prerequisites
- **Runtime**: JDK 21 LTS (e.g. Amazon Corretto 21, Eclipse Temurin 21)
- **Environment Variables**:
  - `JAVA_HOME` must point to JDK 21 installation
  - Example (Windows): `$env:JAVA_HOME="C:\Users\Aleksandr\.sdkman\candidates\java\21.0.9-amzn"`
- **Daemon Requirements**: Docker Daemon must be running (required for docker-compose and Testcontainers)

---

## Preflight Verification Command
- **Preflight Check**: `python python-runtime/preflight_check.py`

---

## Execution Commands

> All commands are run from the **project root** (`link-shortener/`).

- **Compile / Build Check**: `.\gradlew.bat build -x test --no-daemon`
- **Unit / Slice Test**: `.\gradlew.bat test --no-daemon`
- **Integration / E2E Test**: `.\gradlew.bat integrationTest --no-daemon`
- **Run Single Test**: `.\gradlew.bat test --tests "{test_class}" --no-daemon`
- **Database Migration**: `.\gradlew.bat flywayMigrate --no-daemon`
- **Run App (local, requires docker-compose infra)**: `.\gradlew.bat quarkusDev --no-daemon`
- **Build Docker Image**: `docker compose build app`
- **Start All (Compose)**: `docker compose -p link-shortener up -d --build --force-recreate`
- **Stop All (Compose)**: `docker compose -p link-shortener down -v --remove-orphans`

---

## Pre-Approved Command Allowlist
- `.\gradlew.bat build -x test --no-daemon`
- `.\gradlew.bat test --no-daemon`
- `.\gradlew.bat test --tests "{test_class}" --no-daemon`
- `.\gradlew.bat integrationTest --no-daemon`
- `.\gradlew.bat flywayMigrate --no-daemon`
- `.\gradlew.bat quarkusDev --no-daemon`
- `.\gradlew.bat clean --no-daemon`
- `docker compose -p link-shortener up -d --build --force-recreate`
- `docker compose -p link-shortener down -v --remove-orphans`
- `docker compose -p link-shortener ps`
- `docker compose -p link-shortener logs app`
- `python python-runtime/preflight_check.py`
- `python python-runtime/gate_runner.py . COMPILE_CHECK`
- `python python-runtime/gate_runner.py . UNIT_RED_PHASE`
- `python python-runtime/gate_runner.py . UNIT_GREEN_PHASE`
- `python python-runtime/gate_runner.py . INTEGRATION_GREEN_PHASE`
