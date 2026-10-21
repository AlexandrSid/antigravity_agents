package com.linkshortener.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheKeyUtilTest {

    @Test
    @DisplayName("Task 1.2: testCacheKeyUtil_hashesLongUrlConsistently")
    void testCacheKeyUtil_hashesLongUrlConsistently() {
        String url = "https://example.com/some/deep/path?query=param&other=123";
        String key1 = CacheKeyUtil.longUrlToShortKey(url);
        String key2 = CacheKeyUtil.longUrlToShortKey(url);

        assertNotNull(key1);
        assertEquals(key1, key2, "SHA-256 key hashing must be deterministic");
        assertTrue(key1.startsWith("long:"), "Key must start with 'long:' prefix");
        assertEquals(5 + 64, key1.length(), "Key should be 'long:' (5 chars) + 64 hex chars");
    }

    @Test
    @DisplayName("Task 1.2: testCacheKeyUtil_shortKeyToLongKeyPrefix")
    void testCacheKeyUtil_shortKeyToLongKeyPrefix() {
        String shortKey = "aB3dEfGh";
        String cacheKey = CacheKeyUtil.shortKeyToLongKey(shortKey);

        assertNotNull(cacheKey);
        assertEquals("short:aB3dEfGh", cacheKey, "Key must be prefixed with 'short:'");
    }
}
