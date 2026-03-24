package com.powerrag.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaIntrospector Unit Tests")
class SchemaIntrospectorTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    SchemaIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new SchemaIntrospector(jdbcTemplate);
        ReflectionTestUtils.setField(introspector, "allowedTablesConfig", "documents,interactions");
    }

    @Test
    @DisplayName("describe() returns table and column names for allowed tables")
    void describe_returnsSchemaWithTablesAndColumns() {
        when(jdbcTemplate.queryForList(any(String.class), eq("documents")))
                .thenReturn(List.of(
                        Map.of("column_name", "id",        "data_type", "uuid"),
                        Map.of("column_name", "file_name", "data_type", "character varying"),
                        Map.of("column_name", "status",    "data_type", "character varying")
                ));
        when(jdbcTemplate.queryForList(any(String.class), eq("interactions")))
                .thenReturn(List.of(
                        Map.of("column_name", "id",         "data_type", "uuid"),
                        Map.of("column_name", "query_text", "data_type", "text")
                ));

        introspector.afterPropertiesSet();
        String schema = introspector.describe();

        assertThat(schema).contains("Table: documents");
        assertThat(schema).contains("id (uuid)");
        assertThat(schema).contains("file_name (character varying)");
        assertThat(schema).contains("Table: interactions");
        assertThat(schema).contains("query_text (text)");
    }

    @Test
    @DisplayName("describe() skips tables with no columns")
    void describe_skipsEmptyTables() {
        when(jdbcTemplate.queryForList(any(String.class), eq("documents")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(any(String.class), eq("interactions")))
                .thenReturn(List.of(
                        Map.of("column_name", "id", "data_type", "uuid")
                ));

        introspector.afterPropertiesSet();
        String schema = introspector.describe();

        assertThat(schema).doesNotContain("Table: documents");
        assertThat(schema).contains("Table: interactions");
    }

    @Test
    @DisplayName("afterPropertiesSet() survives DB failure — describe() returns fallback")
    void afterPropertiesSet_dbFailure_returnsFallback() {
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        introspector.afterPropertiesSet();

        assertThat(introspector.describe()).isEqualTo("Schema not available");
    }

    @Test
    @DisplayName("refresh() re-reads schema and updates cached description")
    void refresh_updatesSchemaDescription() {
        // First call: no columns
        when(jdbcTemplate.queryForList(any(String.class), eq("documents")))
                .thenReturn(List.of())
                .thenReturn(List.of(Map.of("column_name", "id", "data_type", "uuid")));
        when(jdbcTemplate.queryForList(any(String.class), eq("interactions")))
                .thenReturn(List.of());

        introspector.afterPropertiesSet();
        assertThat(introspector.describe()).doesNotContain("Table: documents");

        // Second call: columns now available
        introspector.refresh();
        assertThat(introspector.describe()).contains("Table: documents");
    }
}
