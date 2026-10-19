# PostgreSQL Tech Stack Standards

> **Stack Profile**: Database / PostgreSQL (15+ / 16+)

---

## 1. Dialect & Indexing Features
1. **Partial Unique Indexes**:
   - PostgreSQL fully supports predicate indexes. Uniqueness for active soft-deleted records should be declared directly in Flyway/Liquibase DDL:
     ```sql
     CREATE UNIQUE INDEX idx_users_active_email ON users (email) WHERE is_deleted = false;
     ```
2. **Identity & Sequence Reset**:
   - When inserting explicit primary key IDs in seed data, reset the underlying sequence:
     ```sql
     SELECT setval(pg_get_serial_sequence('users', 'id'), coalesce(max(id), 1) + 1, false) FROM users;
     ```

---

## 2. Container & Healthcheck Configuration
1. **Healthcheck Contract**:
   ```yaml
   healthcheck:
     test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
     interval: 3s
     timeout: 3s
     retries: 5
   ```
