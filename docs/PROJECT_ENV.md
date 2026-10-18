# Local Project Environment & Commands

## Service Metadata
- **Service Identifier**: user-service
- **Tech Stack**: Kotlin 2.0 / JVM 21, Gradle Kotlin DSL, Quarkus 3.x (Quarkus REST, Hibernate ORM with Panache Kotlin, Jakarta Validation, Jackson Kotlin, Flyway), H2 Database (TCP Server container), Docker Compose

## Execution Commands
- **Compile / Build Check**: `./gradlew compileKotlin`
- **Unit / Slice Test**: `./gradlew test`
- **Integration / E2E Test**: `./gradlew test`
- **Run Single Test**: `./gradlew test --tests "{test_class}"`
- **Database Migration**: `./gradlew flywayMigrate`
