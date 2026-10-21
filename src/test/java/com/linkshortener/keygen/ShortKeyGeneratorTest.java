package com.linkshortener.keygen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortKeyGeneratorTest {

    private ShortKeyGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ShortKeyGeneratorImpl();
    }

    @Test
    @DisplayName("Task 1.1: testGenerateKey_producesExact8Chars")
    void testGenerateKey_producesExact8Chars() {
        String key = generator.generateKey(1L);
        assertNotNull(key);
        assertEquals(8, key.length(), "shortKey must be exactly 8 characters");
        assertTrue(key.matches("^[a-zA-Z0-9]{8}$"), "shortKey must be alphanumeric");
    }

    @Test
    @DisplayName("Task 1.1: testGenerateKey_isDeterministic")
    void testGenerateKey_isDeterministic() {
        String key1 = generator.generateKey(42L);
        String key2 = generator.generateKey(42L);
        assertEquals(key1, key2, "Repeated generation for same ID must be deterministic");
    }

    @Test
    @DisplayName("Task 1.1: testGenerateKey_differentIdsProduceDifferentKeys")
    void testGenerateKey_differentIdsProduceDifferentKeys() {
        String key1 = generator.generateKey(1L);
        String key2 = generator.generateKey(2L);
        assertNotEquals(key1, key2, "Different IDs must produce different keys");
    }

    @Test
    @DisplayName("Task 1.1: testDecodeKey_recoversOriginalId")
    void testDecodeKey_recoversOriginalId() {
        long originalId = 12345L;
        String key = generator.generateKey(originalId);
        OptionalLong decoded = generator.decodeKey(key);
        assertTrue(decoded.isPresent(), "Decoded ID must be present");
        assertEquals(originalId, decoded.getAsLong(), "Decoded ID must match original");
    }

    @Test
    @DisplayName("Task 1.1: testDecodeKey_invalidKeyReturnsEmpty")
    void testDecodeKey_invalidKeyReturnsEmpty() {
        OptionalLong decoded = generator.decodeKey("invalid!");
        assertFalse(decoded.isPresent(), "Invalid key must return empty OptionalLong");
    }
}
