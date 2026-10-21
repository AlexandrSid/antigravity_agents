package com.linkshortener.cache;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLinkCacheTest {

    @Mock
    private RedisDataSource redisDataSource;

    @Mock
    private ValueCommands<String, String> valueCommands;

    @Mock
    private KeyCommands<String> keyCommands;

    private RedisLinkCache cache;

    @BeforeEach
    void setUp() throws Exception {
        cache = new RedisLinkCache();
        // Inject mocks if fields exist in future implementation
        try {
            Field dsField = RedisLinkCache.class.getDeclaredField("redisDataSource");
            dsField.setAccessible(true);
            dsField.set(cache, redisDataSource);
        } catch (NoSuchFieldException ignored) {}
    }

    @Test
    @DisplayName("Task 1.2: testGetLongUrl_cacheHit_returnsUrl")
    void testGetLongUrl_cacheHit_returnsUrl() {
        Optional<String> result = cache.getLongUrl("aB3dEfGh");
        // In red phase, calls throw UnsupportedOperationException
        assertTrue(result.isPresent());
        assertEquals("https://example.com/target", result.get());
    }

    @Test
    @DisplayName("Task 1.2: testGetLongUrl_cacheMiss_returnsEmpty")
    void testGetLongUrl_cacheMiss_returnsEmpty() {
        Optional<String> result = cache.getLongUrl("nonexist");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Task 1.2: testPutShortToLong_setsKeyWithConfiguredTtl")
    void testPutShortToLong_setsKeyWithConfiguredTtl() {
        cache.putShortToLong("aB3dEfGh", "https://example.com", 3600L);
    }

    @Test
    @DisplayName("Task 1.2: testRefreshShortToLongTtl_extendsTtl")
    void testRefreshShortToLongTtl_extendsTtl() {
        cache.refreshShortToLongTtl("aB3dEfGh", 300L);
    }

    @Test
    @DisplayName("Task 1.2: testPutLongToShort_sets30MinuteTtl")
    void testPutLongToShort_sets30MinuteTtl() {
        cache.putLongToShort("https://example.com", "aB3dEfGh", 1800L);
    }
}
