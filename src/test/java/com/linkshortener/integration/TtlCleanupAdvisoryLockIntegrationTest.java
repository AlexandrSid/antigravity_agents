package com.linkshortener.integration;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class TtlCleanupAdvisoryLockIntegrationTest {

    private static final long ADVISORY_LOCK_ID = 42L;

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    static void setUpAll() {
        postgres.start();
    }

    @AfterAll
    static void tearDownAll() {
        if (postgres.isRunning()) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("Task 2.4: Verify PostgreSQL pg_try_advisory_xact_lock prevents concurrent execution")
    void testAdvisoryLock_mutualExclusionBetweenTransactions() throws Exception {
        try (Connection conn1 = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Connection conn2 = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            // Instance 1 acquires the transactional advisory lock
            try (Statement stmt1 = conn1.createStatement()) {
                ResultSet rs1 = stmt1.executeQuery("SELECT pg_try_advisory_xact_lock(" + ADVISORY_LOCK_ID + ")");
                assertTrue(rs1.next(), "Query must return a result row");
                assertTrue(rs1.getBoolean(1), "Instance 1 must successfully acquire advisory lock 42");

                // Instance 2 attempts to acquire the lock concurrently while instance 1 is holding it
                try (Statement stmt2 = conn2.createStatement()) {
                    ResultSet rs2 = stmt2.executeQuery("SELECT pg_try_advisory_xact_lock(" + ADVISORY_LOCK_ID + ")");
                    assertTrue(rs2.next(), "Query must return a result row");
                    assertFalse(rs2.getBoolean(1), "Instance 2 must NOT acquire lock while Instance 1 holds it (mutual exclusion)");
                }

                // Instance 1 finishes job and commits transaction (releasing the xact lock)
                conn1.commit();
            }

            // Instance 2 should now be able to acquire the lock after Instance 1 transaction ended
            try (Statement stmt2 = conn2.createStatement()) {
                ResultSet rs3 = stmt2.executeQuery("SELECT pg_try_advisory_xact_lock(" + ADVISORY_LOCK_ID + ")");
                assertTrue(rs3.next(), "Query must return a result row");
                assertTrue(rs3.getBoolean(1), "Instance 2 must successfully acquire lock after Instance 1 released it");
                conn2.commit();
            }
        }
    }
}
