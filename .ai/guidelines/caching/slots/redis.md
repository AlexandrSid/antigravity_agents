# Redis Caching Slot Implementation

> **Layer**: `caching`  
> **Implements**: `.ai/guidelines/caching/META.md`  
> **Target**: Redis 7.x / Quarkus Redis / Spring Data Redis

---

## 1. CACHE_KEY_NAMESPACING
- Key Pattern: `{service-id}:{entity}:{identifier}` (e.g. `linkshortener:links:abc12345`)
- List/Query Key: `{service-id}:{entity}:query:{hash}`

## 2. TTL_CONFIGURATION
- Entity Cache Default TTL: `3600` seconds (1 hour).
- Query Cache Default TTL: `300` seconds (5 minutes).

## 3. CLIENT_SETUP_AND_FALLBACK
Catch Redis connection exceptions and fallback directly to database query:
```java
try {
    return redisClient.get(key);
} catch (Exception e) {
    logger.warn("Redis lookup failed, falling back to database", e);
    return repository.findByKey(key);
}
```
