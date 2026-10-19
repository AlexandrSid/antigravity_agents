# Core Database Migration & Persistence Standards

> **Scope**: Universal & Platform-Agnostic  
> **Mandatory**: Applies to all relational and SQL database migrations across all frameworks (Flyway, Liquibase, Alembic, Goose, etc.).

---

## 1. Pure Declarative SQL Mandate
1. **ANSI SQL Standard**:
   - All migration files (DDL) and seed scripts (DML) must consist strictly of deterministic, declarative ANSI SQL.
2. **Prohibition of Host-Language Dynamic Logic**:
   - Embedding compiled host-language dynamic functions, runtime aliases (e.g., dynamic Java aliases `CREATE ALIAS`, Python user-defined functions, or external procedural binaries) into migration scripts is **strictly forbidden**.
   - Reasons:
     - Runtime JVM/Python version changes introduce keyword collisions (e.g. `_` reserved in Java 21).
     - External compilers inside database engines lack classpath visibility and break containerized portability.
3. **Deterministic Seed Data**:
   - All fixture and seed data must be structured as standard static batch SQL statements (`INSERT INTO ... VALUES (...)`).

---

## 2. Sequence & Identity Reset Contract
1. **Explicit ID Collision Invariant**:
   - When seed scripts or migration files insert pre-defined primary keys (e.g., `id = 1..1000`), the database internal identity generator or sequence does not automatically advance.
2. **Mandatory Sequence Realignment**:
   - Any migration script that inserts explicit primary keys **must conclude with an explicit sequence restart statement** aligned to the target database dialect:
     - Target value: `MAX(id) + 1`.
   - Failing to realign sequences causes subsequent application `INSERT` operations to crash with primary key / unique index violation errors.

---

## 3. Backward Compatibility & Zero Drift
1. **Immutable Migrations**:
   - Once a migration script is committed and verified by a Quality Gate, its content is permanently immutable. Never modify an existing migration file in place.
2. **Forward-Only Evolution**:
   - Fixes, index additions, and column alterations must be authored as subsequent versioned migration files (e.g. `V2__...`, `V3__...`).
