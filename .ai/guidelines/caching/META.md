# Universal Caching & Performance Meta-Standard

> **Layer**: `caching`  
> **Scope**: Universal & Platform-Agnostic  
> **Applicability**: Active pipelines implementing in-memory, distributed, or HTTP caching.

---

## 1. Architectural Purpose
This meta-standard defines universal rules for caching patterns, key structures, TTL enforcement, and cache invalidation.
It guarantees fresh data consistency, prevents Cache Stampede / Cache Penetration, and avoids stale state issues.

---

## 2. Universal Caching Invariants

### 2.1. Mandatory TTL (Time-To-Live)
1. Every cached entry MUST have an explicit TTL expiration. Permanent keys without TTL are strictly forbidden.

### 2.2. Invalidation on Mutation
1. Write operations (`POST`, `PUT`, `DELETE`, state transitions) must synchronously evict or update the associated cached identity.
2. Invalidate parent and search caches when modifying child entities.

### 2.3. Cache-Aside Pattern & Safe Fallback
1. Cache misses must query the primary database and repopulate the cache gracefully.
2. Redis/cache outages must never crash business requests — gracefully degrade to direct database queries.

---

## 3. Slot Specification Schema (Contract for `slots/<tech>.md`)

| Slot Identifier | Format | Description |
|---|---|---|
| `CACHE_KEY_NAMESPACING` | Markdown rules | Hierarchical key prefixing conventions. |
| `TTL_CONFIGURATION` | Code block | Default TTL settings. |
| `CLIENT_SETUP_AND_FALLBACK` | Code block | Client connection pool and graceful degradation setup. |
