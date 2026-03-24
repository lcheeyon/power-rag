package com.powerrag.sql;

import com.powerrag.sql.exception.SqlValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SqlValidator Unit Tests")
class SqlValidatorTest {

    SqlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SqlValidator();
    }

    // ── Valid SELECT queries ─────────────────────────────────────────────────

    @Test
    @DisplayName("accepts simple SELECT")
    void validate_simpleSelect_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validate("SELECT * FROM documents"));
    }

    @Test
    @DisplayName("accepts SELECT with WHERE and ORDER BY")
    void validate_selectWithClauses_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validate(
                        "SELECT id, file_name FROM documents WHERE status = 'INDEXED' ORDER BY created_at DESC"));
    }

    @Test
    @DisplayName("accepts SELECT with trailing semicolon")
    void validate_selectWithTrailingSemicolon_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validate("SELECT COUNT(*) FROM interactions;"));
    }

    @Test
    @DisplayName("accepts SELECT in uppercase")
    void validate_selectUppercase_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validate("SELECT id FROM documents"));
    }

    @Test
    @DisplayName("accepts SELECT in mixed case")
    void validate_selectMixedCase_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validate("select id from documents"));
    }

    @Test
    @DisplayName("accepts WITH ... SELECT (CTE)")
    void validate_cte_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validate(
                        "WITH recent AS (SELECT * FROM interactions) SELECT * FROM recent"));
    }

    // ── Rejected statements ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO documents (file_name) VALUES ('x')",
            "UPDATE documents SET status = 'FAILED' WHERE id = '1'",
            "DELETE FROM documents WHERE id = '1'",
            "DROP TABLE documents",
            "CREATE TABLE evil (id INT)",
            "ALTER TABLE documents ADD COLUMN x TEXT",
            "TRUNCATE TABLE documents",
            "GRANT ALL ON documents TO public",
            "REVOKE ALL ON documents FROM public"
    })
    @DisplayName("rejects DML and DDL statements")
    void validate_dmlDdl_throws(String sql) {
        assertThatThrownBy(() -> validator.validate(sql))
                .isInstanceOf(SqlValidationException.class);
    }

    @Test
    @DisplayName("rejects multi-statement queries")
    void validate_multiStatement_throws() {
        assertThatThrownBy(() -> validator.validate(
                "SELECT * FROM documents; DELETE FROM documents"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("Multi-statement");
    }

    @Test
    @DisplayName("rejects blank SQL")
    void validate_blank_throws() {
        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(SqlValidationException.class);
    }

    @Test
    @DisplayName("rejects null SQL")
    void validate_null_throws() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(SqlValidationException.class);
    }
}
