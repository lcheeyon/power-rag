package com.powerrag.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies PostgreSQL connectivity and Flyway migration correctness.
 * Uses Testcontainers to spin up a real PostgreSQL 16 instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("PostgreSQL Connectivity and Migration Tests")
class PostgresConnectivityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Database connection is established")
    void databaseConnectionIsEstablished() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("Flyway migration V1 creates all required tables")
    void flywayMigrationCreatesAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """,
                String.class
        );

        assertThat(tables).contains(
                "users",
                "user_roles",
                "interactions",
                "feedback",
                "guardrail_flags"
        );
    }

    @Test
    @DisplayName("Users table has correct columns")
    void usersTableHasCorrectColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'users'
                ORDER BY column_name
                """,
                String.class
        );

        assertThat(columns).contains(
                "id", "username", "password_hash", "email",
                "active", "preferred_language", "created_at", "updated_at"
        );
    }

    @Test
    @DisplayName("Interactions table has JSONB sources column")
    void interactionsTableHasJsonbSourcesColumn() {
        Map<String, Object> col = jdbcTemplate.queryForMap(
                """
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'interactions'
                  AND column_name = 'sources'
                """
        );
        assertThat(col.get("data_type")).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("Seed admin user exists after migration")
    void seedAdminUserExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'admin'",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Admin user has ADMIN and USER roles")
    void adminUserHasCorrectRoles() {
        List<String> roles = jdbcTemplate.queryForList(
                """
                SELECT r.role FROM user_roles r
                JOIN users u ON u.id = r.user_id
                WHERE u.username = 'admin'
                ORDER BY r.role
                """,
                String.class
        );
        assertThat(roles).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    @DisplayName("Guardrail flags stage check constraint is enforced")
    void guardrailStageConstraintIsEnforced() {
        // Insert with invalid stage should throw DataIntegrityViolationException
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.execute(
                        "INSERT INTO guardrail_flags (stage, rule_triggered, severity) " +
                        "VALUES ('INVALID', 'test', 'WARN')"
                )
        );
    }

    @Test
    @DisplayName("Required indexes exist on interactions table")
    void requiredIndexesExistOnInteractions() {
        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname FROM pg_indexes
                WHERE tablename = 'interactions'
                ORDER BY indexname
                """,
                String.class
        );
        assertThat(indexes).contains(
                "idx_interactions_created",
                "idx_interactions_model",
                "idx_interactions_language",
                "idx_interactions_cache_hit"
        );
    }
}
