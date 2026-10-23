# PostgreSQL Persistence Slot Implementation

> **Layer**: `persistence`  
> **Implements**: `.ai/guidelines/persistence/META.md`  
> **Target**: Database / PostgreSQL 15+ / 16+

---

## 1. MIGRATION_TOOL_AND_NAMING
- **Tool**: Flyway
- **Location**: `src/main/resources/db/migration/`
- **File Pattern**: `V{version}__{description}.sql` (e.g., `V1__init_schema.sql`)

## 2. PARTIAL_INDEX_SUPPORT
PostgreSQL fully supports predicate indexes. Uniqueness for active soft-deleted records should be declared directly in DDL:
```sql
CREATE UNIQUE INDEX idx_links_active_short_code ON links (short_code) WHERE is_deleted = false;
```

## 3. SEQUENCE_REALIGNMENT_SYNTAX
When seed migrations insert explicit primary key IDs, reset the underlying sequence:
```sql
SELECT setval(pg_get_serial_sequence('links', 'id'), coalesce(max(id), 1) + 1, false) FROM links;
```

## 4. CONTAINER_HEALTHCHECK_CONFIG
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
  interval: 3s
  timeout: 3s
  retries: 5
```
