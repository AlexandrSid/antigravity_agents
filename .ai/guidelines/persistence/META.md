# Core Database Persistence & Migrations Meta-Standard

> **Layer**: `persistence`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: All active pipelines creating, evolving, or testing relational database schemas, DDL, and migrations.

---

## 1. Architectural Purpose
This meta-standard defines universal rules for database schema evolution, migration immutability, identity sequence integrity, and index design.
It guarantees deterministic schema state across environments, eliminates ID collision crashes, and prevents database vendor syntax deadlocks.

---

## 2. Universal Persistence Invariants

### 2.1. Pure Declarative ANSI SQL Mandate
1. All migration scripts (DDL) and seed scripts (DML) must consist strictly of deterministic, declarative ANSI SQL.
2. Embedding host-language dynamic functions (e.g. Java aliases, Python UDFs, external binaries) inside migration scripts is strictly forbidden.
3. All seed data must be structured as standard static batch SQL statements (`INSERT INTO ... VALUES (...)`).

### 2.2. Sequence & Identity Reset Contract
1. When seed scripts or migration files insert pre-defined primary keys (e.g., `id = 1..1000`), the database internal identity sequence does not advance automatically.
2. Any migration script that inserts explicit primary keys **must conclude with an explicit sequence restart statement** aligned to the target database dialect to set the next value to `MAX(id) + 1`.
3. Failing to realign sequences causes subsequent application `INSERT` operations to crash with primary key / unique index violation errors.

### 2.3. Backward Compatibility & Immutability
1. Once a migration script is committed and verified by a Quality Gate, its content is permanently immutable. Never modify an existing migration file in place.
2. All schema changes, column alterations, and index additions must be authored as subsequent versioned migration files (e.g. `V2__...`, `V3__...`).

### 2.4. Soft Delete Index Invariant
1. Soft delete domain uniqueness is required only among active records (`is_deleted = false`).
2. If the target database supports partial/predicate indexes, enforce uniqueness via a conditional index in DDL.
3. If the target database does not support partial indexes, uniqueness MUST be enforced at the transactional application service layer.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

Every database slot under `slots/<tech>.md` MUST implement the following slots:

| Slot Identifier | Format | Description |
|---|---|---|
| `MIGRATION_TOOL_AND_NAMING` | Markdown text | Migration tool (Flyway, Liquibase, etc.), naming convention, and target folder. |
| `PARTIAL_INDEX_SUPPORT` | Code block or note | Syntax for active-record unique index, or explicit statement of lack of support. |
| `SEQUENCE_REALIGNMENT_SYNTAX` | Code block | Dialect-specific SQL statement to restart/align auto-increment sequence to `MAX(id) + 1`. |
| `CONTAINER_HEALTHCHECK_CONFIG` | Code block | Docker / Compose healthcheck definition verifying ready database connections. |

---

## 4. Agent Consumption Protocol
1. **Discovery**: Read `SPEC.md` (`## Applicable Technical Standards`) to identify the persistence database slot.
2. **Extraction**: Open `.ai/guidelines/persistence/slots/<tech>.md` and apply its DDL, index, sequence, and container conventions.
