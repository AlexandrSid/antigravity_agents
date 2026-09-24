package com.userservice.integration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseIntegrationTest extends IntegrationTestSupport {

    @Test
    void schema_containsExpectedTablesConstraintsGeneratedColumnsAndIndexes() throws Exception {
        assertThat(scalarLong("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND UPPER(TABLE_NAME) IN (
                    'USERS',
                    'ADDRESSES',
                    'FLYWAY_SCHEMA_HISTORY'
                  )
                """)).isEqualTo(3);
        assertThat(scalarLong("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND TABLE_NAME = 'USERS'
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """)).isEqualTo(3);
        assertThat(scalarLong("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND TABLE_NAME = 'USERS'
                  AND COLUMN_NAME IN ('ACTIVE_EMAIL', 'ACTIVE_PHONE')
                  AND IS_GENERATED = 'ALWAYS'
                """)).isEqualTo(2);
        assertThat(scalarLong("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND INDEX_NAME IN (
                    'UX_USERS_ACTIVE_EMAIL',
                    'UX_USERS_ACTIVE_PHONE',
                    'IX_USERS_FATHER',
                    'IX_USERS_MOTHER',
                    'UX_ADDRESSES_NORMALIZED_TEXT'
                  )
                """)).isEqualTo(5);
        assertThat(scalarLong("""
                SELECT COUNT(*) FROM "flyway_schema_history"
                WHERE "success" = TRUE
                  AND "version" IS NOT NULL
                """)).isEqualTo(2);
    }

    @Test
    void seed_containsTwentyAddressesAndOneThousandChronologicalUsers() throws Exception {
        assertThat(scalarLong("SELECT COUNT(*) FROM ADDRESSES WHERE ID BETWEEN 1 AND 20")).isEqualTo(20);
        assertThat(scalarLong("""
                SELECT COUNT(*) FROM USERS
                WHERE ID BETWEEN 1 AND 1000 AND IS_DELETED = FALSE
                """)).isEqualTo(1_000);
        assertThat(scalarLong("""
                SELECT MIN(user_count)
                FROM (
                  SELECT ADDRESS_ID, COUNT(*) user_count
                  FROM USERS WHERE ID BETWEEN 1 AND 1000
                  GROUP BY ADDRESS_ID
                )
                """)).isGreaterThanOrEqualTo(10);
        assertThat(scalarLong("""
                SELECT MAX(user_count)
                FROM (
                  SELECT ADDRESS_ID, COUNT(*) user_count
                  FROM USERS WHERE ID BETWEEN 1 AND 1000
                  GROUP BY ADDRESS_ID
                )
                """)).isLessThanOrEqualTo(120);
        assertThat(scalarLong("""
                SELECT COUNT(DISTINCT user_count)
                FROM (
                  SELECT ADDRESS_ID, COUNT(*) user_count
                  FROM USERS WHERE ID BETWEEN 1 AND 1000
                  GROUP BY ADDRESS_ID
                )
                """)).isGreaterThan(1);
        assertThat(scalarLong("""
                SELECT COUNT(*)
                FROM USERS child
                LEFT JOIN USERS father ON father.ID = child.FATHER_ID
                LEFT JOIN USERS mother ON mother.ID = child.MOTHER_ID
                WHERE child.ID BETWEEN 1 AND 1000
                  AND ((child.FATHER_ID IS NOT NULL AND father.ID IS NULL)
                   OR (child.MOTHER_ID IS NOT NULL AND mother.ID IS NULL)
                   OR (father.ID IS NOT NULL AND father.BIRTH_DATE >= child.BIRTH_DATE)
                   OR (mother.ID IS NOT NULL AND mother.BIRTH_DATE >= child.BIRTH_DATE))
                """)).isZero();
        assertThat(scalarLong("""
                SELECT COUNT(*)
                FROM USERS child
                JOIN USERS parent ON parent.ID IN (child.FATHER_ID, child.MOTHER_ID)
                WHERE child.ID BETWEEN 1 AND 1000
                  AND (parent.FATHER_ID IS NOT NULL OR parent.MOTHER_ID IS NOT NULL)
                """)).isGreaterThan(0);
    }

    @Test
    void databaseEnforcesActiveIdentityUniquenessAndAllowsReuseAfterSoftDelete() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> statement.executeUpdate("""
                        INSERT INTO USERS
                          (FIRST_NAME, LAST_NAME, BIRTH_DATE, EMAIL, PHONE_NUMBER, IS_DELETED)
                        VALUES
                          ('Duplicate', 'Email', DATE '1990-01-01',
                           'user1@example.com', '+19999999991', FALSE)
                        """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.executeUpdate("""
                        INSERT INTO USERS
                          (FIRST_NAME, LAST_NAME, BIRTH_DATE, EMAIL, PHONE_NUMBER, IS_DELETED)
                        VALUES
                          ('Duplicate', 'Phone', DATE '1990-01-01',
                           'unique.db@example.com', '+15550000001', FALSE)
                        """)).isInstanceOf(SQLException.class);

                statement.executeUpdate("UPDATE USERS SET IS_DELETED = TRUE WHERE ID = 1");
                assertThat(statement.executeUpdate("""
                        INSERT INTO USERS
                          (FIRST_NAME, LAST_NAME, BIRTH_DATE, EMAIL, PHONE_NUMBER, IS_DELETED)
                        VALUES
                          ('Reused', 'Identity', DATE '1990-01-01',
                           'user1@example.com', '+15550000001', FALSE)
                        """)).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }
}
