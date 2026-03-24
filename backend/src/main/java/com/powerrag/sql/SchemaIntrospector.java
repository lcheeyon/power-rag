package com.powerrag.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads PostgreSQL table/column metadata at startup and exposes it as a formatted
 * schema description string for use in LLM prompts.
 *
 * Only the tables listed in {@code powerrag.text-to-sql.allowed-tables} are exposed.
 */
@Slf4j
@Component
public class SchemaIntrospector implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    @Value("${powerrag.text-to-sql.allowed-tables:documents,interactions,feedback}")
    private String allowedTablesConfig;

    private volatile String schemaDescription = "Schema not available";

    public SchemaIntrospector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            schemaDescription = buildDescription();
            log.info("SchemaIntrospector loaded schema for tables: {}", allowedTablesConfig);
        } catch (Exception e) {
            log.warn("SchemaIntrospector could not load schema at startup: {}", e.getMessage());
        }
    }

    /** Returns the cached schema description string used in LLM prompts. */
    public String describe() {
        return schemaDescription;
    }

    /** Re-reads schema from the database. Safe to call at runtime after DDL changes. */
    public void refresh() {
        afterPropertiesSet();
    }

    /**
     * For varchar columns whose name suggests an enum (status, type, category, recommendation),
     * fetches the distinct values from the DB and returns a hint string like " [values: A, B, C]".
     * Returns empty string for all other columns or if the query fails.
     */
    private String enumHint(String table, String column, String dataType) {
        if (!dataType.contains("character varying") && !dataType.equals("text")) return "";
        String col = column.toLowerCase();
        if (!col.equals("status") && !col.equals("org_type") && !col.equals("recommendation")
                && !col.endsWith("_type") && !col.endsWith("_status")) return "";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT " + column + " FROM " + table
                    + " WHERE " + column + " IS NOT NULL ORDER BY " + column);
            if (rows.isEmpty() || rows.size() > 15) return "";
            String values = rows.stream()
                    .map(r -> "'" + r.get(column) + "'")
                    .collect(Collectors.joining(", "));
            return " [values: " + values + "]";
        } catch (Exception e) {
            return "";
        }
    }

    /** Package-visible for testing. */
    String buildDescription() {
        List<String> tables = Arrays.stream(allowedTablesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                    "SELECT column_name, data_type " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = ? AND table_schema = 'public' " +
                    "ORDER BY ordinal_position",
                    table);

            if (cols.isEmpty()) {
                log.debug("SchemaIntrospector: table '{}' not found or has no columns", table);
                continue;
            }

            String colList = cols.stream()
                    .map(row -> {
                        String colName = (String) row.get("column_name");
                        String dataType = (String) row.get("data_type");
                        String hint = enumHint(table, colName, dataType);
                        return colName + " (" + dataType + ")" + hint;
                    })
                    .collect(Collectors.joining(", "));

            sb.append("Table: ").append(table).append("\n")
              .append("Columns: ").append(colList).append("\n\n");
        }
        return sb.toString().strip();
    }
}
