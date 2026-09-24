# Local Project Environment & Commands

## Service Metadata
- **Service Identifier**: user-service
- **Tech Stack**: Java 21; Gradle 8.10.2 with Kotlin DSL; Spring Boot 3.3.6 (Web, Data JPA, Validation); Flyway 10.10.0; Lombok; H2 2.2.224 TCP server; Docker Compose
- **Host Command Syntax**: Windows PowerShell

## Prerequisites
- Java 21 available through a valid `JAVA_HOME`.
- Docker Engine / Docker Desktop with Compose v2.
- The repository-local Gradle wrapper generated from Gradle 8.10.2.
- Start the local database before migration or application startup: `docker compose up -d h2-db`.

## Execution Commands
- **Compile / Build Check**: `.\gradlew.bat testClasses`
- **Run All Tests**: `.\gradlew.bat test`
- **Run Single Test**: `.\gradlew.bat test --tests "{test_class}"`
- **Database Migration**: `.\gradlew.bat flywayMigrate`

## Runtime Commands
- **Start Local Database**: `docker compose up -d h2-db`
- **Run Application Locally**: `.\gradlew.bat bootRun`
- **Run Full Compose Stack**: `docker compose up --build`
- **Stop Compose Stack**: `docker compose down`

## Command Contract
- Downstream agents must invoke the command bound to the relevant field exactly as written.
- `Compile / Build Check` uses `testClasses` so both production and test type surfaces are compiled without executing tests.
- `Run All Tests` is reserved for the in-memory Unit/Slice suite described by `TDD_PLAN.md` Section 1.
- `Database Migration` requires the H2 TCP service to be running first.
