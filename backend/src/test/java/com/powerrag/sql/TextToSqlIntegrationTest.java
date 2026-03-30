package com.powerrag.sql;

import com.powerrag.infrastructure.TestContainersConfig;
import com.powerrag.sql.exception.SqlValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for TextToSqlService against a real PostgreSQL Testcontainer.
 * GoogleGenAiChatModel is mocked — Text-to-SQL uses the {@code geminiPro} ChatClient built from it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("TextToSql Integration Tests")
class TextToSqlIntegrationTest {

    @MockitoBean
    GoogleGenAiChatModel googleGenAiChatModel;

    @Autowired
    TextToSqlService textToSqlService;

    @Autowired
    SchemaIntrospector schemaIntrospector;

    @Autowired
    SqlValidator sqlValidator;

    // ── SchemaIntrospector ───────────────────────────────────────────────────

    @Test
    @DisplayName("SchemaIntrospector loads schema for allowed tables from real DB")
    void schemaIntrospector_loadsSchemaFromDb() {
        String schema = schemaIntrospector.describe();

        assertThat(schema).contains("Table: documents");
        assertThat(schema).contains("file_name");
        assertThat(schema).contains("Table: interactions");
        assertThat(schema).contains("query_text");
        assertThat(schema).contains("Table: feedback");
    }

    // ── TextToSqlService full pipeline ───────────────────────────────────────

    @Test
    @DisplayName("Full pipeline: SELECT COUNT(*) FROM users executes against real DB")
    void query_selectCount_returnsRow() {
        mockLlmResponse("SELECT COUNT(*) FROM users");

        SqlQueryResponse response = textToSqlService.query("How many users are there?", "en");

        assertThat(response.sql()).isEqualTo("SELECT COUNT(*) FROM users");
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.clarification()).isNull();
        assertThat(response.executionError()).isNull();
        // At least the seeded admin user exists
        Object count = response.rows().get(0).get("count");
        assertThat(Long.parseLong(String.valueOf(count))).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Full pipeline: SELECT from documents executes without error")
    void query_selectDocuments_executesWithoutError() {
        mockLlmResponse("SELECT id, file_name, status FROM documents");

        SqlQueryResponse response = textToSqlService.query("List all documents", "en");

        assertThat(response.executionError()).isNull();
        assertThat(response.clarification()).isNull();
        assertThat(response.sql()).isEqualTo("SELECT id, file_name, status FROM documents");
    }

    @Test
    @DisplayName("SqlValidator rejects DELETE — throws SqlValidationException")
    void validator_delete_throws() {
        assertThatThrownBy(() -> sqlValidator.validate("DELETE FROM users WHERE 1=1"))
                .isInstanceOf(SqlValidationException.class);
    }

    @Test
    @DisplayName("SqlValidator accepts valid SELECT — no exception")
    void validator_select_passes() {
        sqlValidator.validate("SELECT id, username FROM users");
        // no exception expected
    }

    @Test
    @DisplayName("LLM clarification response is surfaced without DB execution")
    void query_clarification_returnsWithoutDbCall() {
        mockLlmResponse("I need more information — which table are you querying?");

        SqlQueryResponse response = textToSqlService.query("Show me the data", "en");

        assertThat(response.clarification()).contains("I need more information");
        assertThat(response.sql()).isNull();
        assertThat(response.rowCount()).isEqualTo(0);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private void mockLlmResponse(String content) {
        ChatResponse mockResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(content))));
        when(googleGenAiChatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }
}
