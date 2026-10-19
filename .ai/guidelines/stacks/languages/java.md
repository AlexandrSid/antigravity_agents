# Java Tech Stack Standards

> **Stack Profile**: Language / Java 21 LTS

---

## 1. Runtime & Keyword Constraints
1. **Target Java Version**: Java 21 LTS.
2. **Underscore Keyword Restriction**:
   - In Java 21, the single underscore `_` is a strictly reserved keyword and cannot be used as an identifier, variable, parameter name, or dynamically generated alias.
3. **Modern Idioms**:
   - Prefer Java `record` for immutable DTOs and data carriers.
   - Use Pattern Matching for `switch` and `instanceof`.

---

## 2. Toolchain Prerequisites
1. **JAVA_HOME Invariant**:
   - Build scripts and execution runners must verify that `JAVA_HOME` explicitly points to a JDK 21 installation before launching compilations.
2. **Build Wrapper**:
   - Use `./gradlew` (or `./mvnw`) with verified execution permissions.
