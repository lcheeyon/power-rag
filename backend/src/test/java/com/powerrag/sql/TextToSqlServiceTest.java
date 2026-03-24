package com.powerrag.sql;

import com.powerrag.sql.exception.SqlValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TextToSqlService Unit Tests")
class TextToSqlServiceTest {

    @Mock SchemaIntrospector schemaIntrospector;
    @Mock SqlValidator       sqlValidator;
    @Mock JdbcTemplate       jdbcTemplate;
    @Mock ChatClient         chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec      callSpec;

    TextToSqlService service;

    @BeforeEach
    void setUp() {
        service = new TextToSqlService(schemaIntrospector, sqlValidator, jdbcTemplate, chatClient);
        lenient().when(schemaIntrospector.describe()).thenReturn("Table: documents\nColumns: id (uuid), file_name (text)");
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("query returns rows when LLM returns valid SELECT and DB has results")
    void query_validSelect_returnsRows() {
        when(callSpec.content()).thenReturn("SELECT COUNT(*) FROM documents");
        when(jdbcTemplate.queryForList("SELECT COUNT(*) FROM documents"))
                .thenReturn(List.of(Map.of("count", 5L)));

        SqlQueryResponse response = service.query("How many documents?", "en");

        assertThat(response.sql()).isEqualTo("SELECT COUNT(*) FROM documents");
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.rows().get(0)).containsEntry("count", 5L);
        assertThat(response.clarification()).isNull();
        assertThat(response.executionError()).isNull();
        verify(sqlValidator).validate("SELECT COUNT(*) FROM documents");
    }

    @Test
    @DisplayName("query strips markdown code fences from LLM response")
    void query_markdownCodeBlock_sqlExtracted() {
        when(callSpec.content()).thenReturn("```sql\nSELECT * FROM documents\n```");
        when(jdbcTemplate.queryForList("SELECT * FROM documents")).thenReturn(List.of());

        SqlQueryResponse response = service.query("List all documents", "en");

        assertThat(response.sql()).isEqualTo("SELECT * FROM documents");
        verify(sqlValidator).validate("SELECT * FROM documents");
    }

    @Test
    @DisplayName("query returns clarification when LLM returns non-SQL text")
    void query_clarification_returnsClarificationField() {
        when(callSpec.content()).thenReturn(
                "Could you please specify which time period you are asking about?");

        SqlQueryResponse response = service.query("Show me the data", "en");

        assertThat(response.clarification())
                .isEqualTo("Could you please specify which time period you are asking about?");
        assertThat(response.sql()).isNull();
        assertThat(response.rowCount()).isEqualTo(0);
        verifyNoInteractions(sqlValidator, jdbcTemplate);
    }

    @Test
    @DisplayName("query propagates SqlValidationException when LLM returns DELETE")
    void query_deleteSql_throwsSqlValidationException() {
        when(callSpec.content()).thenReturn("DELETE FROM users WHERE id = '1'");
        doThrow(new SqlValidationException("Only SELECT queries are allowed"))
                .when(sqlValidator).validate(anyString());

        assertThatThrownBy(() -> service.query("Delete all users", "en"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("Only SELECT queries are allowed");
    }

    @Test
    @DisplayName("query returns executionError when JdbcTemplate throws")
    void query_jdbcThrows_returnsExecutionError() {
        when(callSpec.content()).thenReturn("SELECT * FROM documents");
        when(jdbcTemplate.queryForList("SELECT * FROM documents"))
                .thenThrow(new RuntimeException("relation does not exist"));

        SqlQueryResponse response = service.query("List all documents", "en");

        assertThat(response.executionError()).contains("relation does not exist");
        assertThat(response.rowCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("query returns executionError when LLM call throws")
    void query_llmThrows_returnsExecutionError() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("API unavailable"));

        SqlQueryResponse response = service.query("question", "en");

        assertThat(response.executionError()).contains("LLM call failed");
        assertThat(response.sql()).isNull();
    }

    @Test
    @DisplayName("query with empty result set returns columns from first row — no NPE")
    void query_emptyResult_returnsEmptyColumns() {
        when(callSpec.content()).thenReturn("SELECT id FROM documents WHERE id = 'nonexistent'");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        SqlQueryResponse response = service.query("Find a specific document", "en");

        assertThat(response.rowCount()).isEqualTo(0);
        assertThat(response.columns()).isEmpty();
    }

    // ── extractSql helper ────────────────────────────────────────────────────

    @Test
    @DisplayName("extractSql removes leading/trailing whitespace")
    void extractSql_trimmed() {
        assertThat(service.extractSql("  SELECT 1  ")).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("extractSql removes ```sql ... ``` fences")
    void extractSql_sqlFence() {
        assertThat(service.extractSql("```sql\nSELECT 1\n```")).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("extractSql removes plain ``` ... ``` fences")
    void extractSql_plainFence() {
        assertThat(service.extractSql("```\nSELECT 1\n```")).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("extractSql returns empty string for null input")
    void extractSql_null_returnsEmpty() {
        assertThat(service.extractSql(null)).isEmpty();
    }

    // ── looksLikeSql helper ──────────────────────────────────────────────────

    @Test
    @DisplayName("looksLikeSql returns true for SELECT")
    void looksLikeSql_select_true() {
        assertThat(service.looksLikeSql("SELECT 1")).isTrue();
    }

    @Test
    @DisplayName("looksLikeSql returns true for DELETE (so validator catches it)")
    void looksLikeSql_delete_true() {
        assertThat(service.looksLikeSql("DELETE FROM users")).isTrue();
    }

    @Test
    @DisplayName("looksLikeSql returns false for natural language")
    void looksLikeSql_naturalLanguage_false() {
        assertThat(service.looksLikeSql("Could you please clarify?")).isFalse();
    }

    @Test
    @DisplayName("looksLikeSql returns false for blank")
    void looksLikeSql_blank_false() {
        assertThat(service.looksLikeSql("   ")).isFalse();
    }
}
