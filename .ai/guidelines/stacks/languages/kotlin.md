# Kotlin Tech Stack Standards

> **Stack Profile**: Language / Kotlin 2.x / JVM 21

---

## 1. Language & Compiler Constraints
1. **Target Runtime**: Strictly Java 21 LTS (`jvmTarget = "21"`).
2. **Kotlin Version**: Kotlin 2.0+ (K2 compiler).
3. **Null-Safety & DTOs**:
   - DTOs and API contract models must explicitly differentiate between nullable fields (`String?`) and non-nullable required fields (`String`).
   - Jackson Kotlin module or native Quarkus/Kotlin serialization must be configured to reject missing required fields with a 400 Bad Request.

---

## 2. Build Tool & Gradle Kotlin DSL
1. **Gradle Wrapper Mandate**:
   - Every Kotlin project must provide and use a project-local Gradle Wrapper (`gradlew` and `gradlew.bat`).
   - Calling global unversioned `gradle` from the PATH is forbidden.
2. **Build Configuration (`build.gradle.kts`)**:
   - Build scripts must be written in Gradle Kotlin DSL.
   - All compilation tasks must set explicit JVM 21 compatibility:
     ```kotlin
     kotlin {
         jvmToolchain(21)
     }
     ```
