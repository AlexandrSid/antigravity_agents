# [Database Name] Persistence Slot Implementation

> **Layer**: `persistence`  
> **Implements**: `.ai/guidelines/persistence/META.md`

---

## 1. MIGRATION_TOOL_AND_NAMING
- **Tool**: `[Flyway / Liquibase / Alembic / Goose]`
- **Location**: `[e.g. src/main/resources/db/migration/]`
- **File Pattern**: `[e.g. V{version}__{description}.sql]`

## 2. PARTIAL_INDEX_SUPPORT
```sql
-- DDL for partial unique index (or note if unsupported)
```

## 3. SEQUENCE_REALIGNMENT_SYNTAX
```sql
-- Dialect-specific sequence / identity realignment statement
```

## 4. CONTAINER_HEALTHCHECK_CONFIG
```yaml
healthcheck:
  test: ["CMD-SHELL", "..."]
  interval: 3s
  timeout: 3s
  retries: 5
```
