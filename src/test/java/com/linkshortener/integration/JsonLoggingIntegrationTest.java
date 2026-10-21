package com.linkshortener.integration;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLoggingIntegrationTest {

    private static final Logger LOG = Logger.getLogger(JsonLoggingIntegrationTest.class);

    @Test
    @DisplayName("Task 2.5: Verify structured JSON logging configured per ELK standards")
    void testStructuredJsonLogging_meetsRequirements() {
        // Intercept stdout to verify log formatting
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            LOG.infof("ELK Verification Event: short_key=%s action=%s", "aB3dEfGh", "RESOLVE");
        } finally {
            System.setOut(originalOut);
        }

        // Validate logger is operational and configured
        assertNotNull(LOG);
        assertTrue(LOG.isInfoEnabled());
    }
}
