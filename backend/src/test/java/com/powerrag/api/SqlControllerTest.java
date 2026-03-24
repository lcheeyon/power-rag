package com.powerrag.api;

import com.powerrag.sql.SchemaIntrospector;
import com.powerrag.sql.SqlQueryRequest;
import com.powerrag.sql.SqlQueryResponse;
import com.powerrag.sql.TextToSqlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlController Unit Tests")
class SqlControllerTest {

    @Mock TextToSqlService   textToSqlService;
    @Mock SchemaIntrospector schemaIntrospector;

    SqlController controller;

    @BeforeEach
    void setUp() {
        controller = new SqlController(textToSqlService, schemaIntrospector);
    }

    // ── POST /api/sql/query ──────────────────────────────────────────────────

    @Test
    @DisplayName("query() delegates to TextToSqlService and returns 200")
    void query_delegatesToService_returns200() {
        SqlQueryRequest request = new SqlQueryRequest("How many grants?", "en");
        SqlQueryResponse mockResponse = new SqlQueryResponse(
                "SELECT COUNT(*) FROM grant_programs", List.of("count"),
                List.of(), 0, null, null, 42L);
        when(textToSqlService.query("How many grants?", "en")).thenReturn(mockResponse);

        ResponseEntity<SqlQueryResponse> result = controller.query(request, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(mockResponse);
        verify(textToSqlService).query("How many grants?", "en");
    }

    @Test
    @DisplayName("query() defaults language to 'en' when null")
    void query_nullLanguage_defaultsToEn() {
        SqlQueryRequest request = new SqlQueryRequest("Show programs", null);
        when(textToSqlService.query(anyString(), eq("en")))
                .thenReturn(new SqlQueryResponse(null, List.of(), List.of(), 0, null, null, 10L));

        controller.query(request, null);

        verify(textToSqlService).query("Show programs", "en");
    }

    @Test
    @DisplayName("query() passes through non-null language")
    void query_nonNullLanguage_passedThrough() {
        SqlQueryRequest request = new SqlQueryRequest("显示所有项目", "zh");
        when(textToSqlService.query(anyString(), eq("zh")))
                .thenReturn(new SqlQueryResponse(null, List.of(), List.of(), 0, null, null, 10L));

        controller.query(request, null);

        verify(textToSqlService).query("显示所有项目", "zh");
    }

    // ── POST /api/sql/refresh-schema ─────────────────────────────────────────

    @Test
    @DisplayName("refreshSchema() calls SchemaIntrospector.refresh() and returns 200")
    void refreshSchema_callsRefresh_returns200() {
        ResponseEntity<String> result = controller.refreshSchema();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Schema refreshed");
        verify(schemaIntrospector).refresh();
    }

    // ── GET /api/sql/schema ──────────────────────────────────────────────────

    @Test
    @DisplayName("schema() returns current schema description with 200")
    void schema_returnsDescription_200() {
        when(schemaIntrospector.describe()).thenReturn("Table: grant_programs\nColumns: id (integer)");

        ResponseEntity<String> result = controller.schema();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).contains("grant_programs");
        verify(schemaIntrospector).describe();
    }

    @Test
    @DisplayName("schema() returns 'Schema not available' when introspector has no data")
    void schema_notAvailable_returnsPlaceholder() {
        when(schemaIntrospector.describe()).thenReturn("Schema not available");

        ResponseEntity<String> result = controller.schema();

        assertThat(result.getBody()).isEqualTo("Schema not available");
    }
}
