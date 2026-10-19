# Quarkus Tech Stack Standards

> **Stack Profile**: Framework / Quarkus 3.x / Kotlin Panache

---

## 1. Fast-JAR Packaging & Dockerfile
1. **Package Type**:
   - Production Dockerfiles must build using Quarkus Fast-JAR format (`quarkus.package.jar.type=fast-jar`).
2. **Dockerfile Layout**:
   - The multi-stage Dockerfile must copy:
     ```dockerfile
     COPY --chown=185 build/quarkus-app/lib/ /deployments/lib/
     COPY --chown=185 build/quarkus-app/*.jar /deployments/
     COPY --chown=185 build/quarkus-app/app/ /deployments/app/
     COPY --chown=185 build/quarkus-app/quarkus/ /deployments/quarkus/
     ```

---

## 2. Hibernate ORM & Database Dialect Compatibility
1. **Dialect Version Matching**:
   - Quarkus 3.15+ targets H2 versions `>= 2.3.230` by default.
   - If connecting to an external H2 TCP Server running version `2.1.x`, you **must explicitly declare the dialect version** in `src/main/resources/application.properties`:
     ```properties
     quarkus.datasource.db-version=2.1.0
     ```
   - Failing to set this property causes Hibernate ORM bootstrap to fail immediately on startup.

---

## 3. Panache Kotlin Invariants
1. **Entity Boundaries**:
   - Entities extend `PanacheEntity` or `PanacheEntityBase`.
   - Never return Panache entities directly from Resource/Controller endpoints; map to dedicated Response DTOs.
