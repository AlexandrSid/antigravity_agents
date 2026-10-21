package com.linkshortener.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisRealCacheIntegrationTest {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @BeforeAll
    static void setUpAll() {
        redis.start();
    }

    @AfterAll
    static void tearDownAll() {
        if (redis.isRunning()) {
            redis.stop();
        }
    }

    @Test
    @DisplayName("Task 2.3: Verify real Redis 7: setex, get, ttl extension, and eviction")
    void testRedisRealContainer_operationsAndTtlLifecycle() throws Exception {
        String key = "short:aB3dEfGh";
        String value = "https://example.com/target-long-url";

        // 1. Set key with 3600s TTL (1 hour)
        ExecResult setResult = redis.execInContainer("redis-cli", "setex", key, "3600", value);
        assertEquals(0, setResult.getExitCode());
        assertTrue(setResult.getStdout().contains("OK"), "SETEX must return OK");

        // 2. Read key back
        ExecResult getResult = redis.execInContainer("redis-cli", "get", key);
        assertEquals(0, getResult.getExitCode());
        assertEquals(value, getResult.getStdout().trim(), "GET must return exact stored URL");

        // 3. Verify TTL is positive and <= 3600
        ExecResult ttlResult = redis.execInContainer("redis-cli", "ttl", key);
        assertEquals(0, ttlResult.getExitCode());
        long ttl = Long.parseLong(ttlResult.getStdout().trim());
        assertTrue(ttl > 3500 && ttl <= 3600, "TTL must be close to 3600 seconds");

        // 4. Refresh TTL to 300 seconds (5 minutes)
        ExecResult expireResult = redis.execInContainer("redis-cli", "expire", key, "300");
        assertEquals(0, expireResult.getExitCode());
        assertEquals("1", expireResult.getStdout().trim(), "EXPIRE command must return 1 (success)");

        // 5. Verify refreshed TTL is <= 300
        ExecResult ttlRefreshedResult = redis.execInContainer("redis-cli", "ttl", key);
        assertEquals(0, ttlRefreshedResult.getExitCode());
        long refreshedTtl = Long.parseLong(ttlRefreshedResult.getStdout().trim());
        assertTrue(refreshedTtl > 0 && refreshedTtl <= 300, "Refreshed TTL must be <= 300 seconds");

        // 6. Delete key
        ExecResult delResult = redis.execInContainer("redis-cli", "del", key);
        assertEquals(0, delResult.getExitCode());

        // 7. Verify key is absent
        ExecResult getAfterDelResult = redis.execInContainer("redis-cli", "get", key);
        assertEquals(0, getAfterDelResult.getExitCode());
        assertTrue(getAfterDelResult.getStdout().trim().isEmpty()
                || getAfterDelResult.getStdout().contains("nil"), "Key must be deleted");
    }
}
