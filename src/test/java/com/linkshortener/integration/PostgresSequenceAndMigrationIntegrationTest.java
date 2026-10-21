package com.linkshortener.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgresSequenceAndMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    static void setUpAll() {
        postgres.start();

        // Apply Flyway migrations on clean PostgreSQL container
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @AfterAll
    static void tearDownAll() {
        if (postgres.isRunning()) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("Task 2.1: Verify Flyway migration V1 creates short_links table and sequence")
    void testFlywayMigration_createsSchemaSuccessfully() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Check table short_links exists
            ResultSet rsTable = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_name = 'short_links'"
            );
            assertTrue(rsTable.next(), "Table 'short_links' must be created by Flyway migration");

            // Check sequence short_links_seq exists
            ResultSet rsSeq = stmt.executeQuery(
                    "SELECT sequence_name FROM information_schema.sequences WHERE sequence_name = 'short_links_seq'"
            );
            assertTrue(rsSeq.next(), "Sequence 'short_links_seq' must be created by Flyway migration");
        }
    }

    @Test
    @DisplayName("Task 2.2: Verify concurrent ID reservation from short_links_seq with allocationSize=1000")
    void testConcurrentSequenceAllocation_producesUniqueIds() throws Exception {
        int threadCount = 10;
        int allocationsPerThread = 5;
        int totalAllocations = threadCount * allocationsPerThread;

        Set<Long> allocatedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    try (Connection conn = DriverManager.getConnection(
                            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                         Statement stmt = conn.createStatement()) {
                        for (int j = 0; j < allocationsPerThread; j++) {
                            ResultSet rs = stmt.executeQuery("SELECT nextval('short_links_seq')");
                            if (rs.next()) {
                                allocatedIds.add(rs.getLong(1));
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All sequence allocation threads must complete within timeout");
        assertEquals(totalAllocations, allocatedIds.size(), "All allocated sequence values must be unique and non-colliding");
    }
}
