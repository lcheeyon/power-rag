package com.powerrag.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Text-to-SQL pipeline:
 * <ol>
 *   <li>Fetch schema description from {@link SchemaIntrospector}</li>
 *   <li>Build an LLM prompt and call the LLM</li>
 *   <li>Extract SQL from the LLM response</li>
 *   <li>Detect clarification responses (non-SQL text) and return them directly</li>
 *   <li>Validate the SQL via {@link SqlValidator} (throws {@link com.powerrag.sql.exception.SqlValidationException} for non-SELECT)</li>
 *   <li>Execute the SELECT against PostgreSQL via {@link JdbcTemplate}</li>
 *   <li>Return a {@link SqlQueryResponse} with rows and column names</li>
 * </ol>
 */
@Slf4j
@Service
public class TextToSqlService {

    private final SchemaIntrospector schemaIntrospector;
    private final SqlValidator       sqlValidator;
    private final JdbcTemplate       jdbcTemplate;
    private final ChatClient         chatClient;

    public TextToSqlService(SchemaIntrospector schemaIntrospector,
                            SqlValidator sqlValidator,
                            JdbcTemplate jdbcTemplate,
                            @Qualifier("geminiPro") ChatClient chatClient) {
        this.schemaIntrospector = schemaIntrospector;
        this.sqlValidator       = sqlValidator;
        this.jdbcTemplate       = jdbcTemplate;
        this.chatClient         = chatClient;
    }

    public SqlQueryResponse query(String question, String language) {
        long   start  = System.currentTimeMillis();
        String schema = schemaIntrospector.describe();
        String prompt = buildPrompt(question, schema, language);

        // ── 1. Call LLM ──────────────────────────────────────────────────────
        String rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Text-to-SQL LLM call failed: {}", e.getMessage());
            return new SqlQueryResponse(null, List.of(), List.of(), 0, null,
                    "LLM call failed: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }

        // ── 2. Extract SQL from response ─────────────────────────────────────
        String sql = extractSql(rawResponse);

        // ── 3. Clarification detection ───────────────────────────────────────
        if (!looksLikeSql(sql)) {
            log.debug("Text-to-SQL: LLM returned clarification instead of SQL");
            return new SqlQueryResponse(null, List.of(), List.of(), 0, sql, null,
                    System.currentTimeMillis() - start);
        }

        // ── 4. Validate (throws SqlValidationException for non-SELECT) ────────
        sqlValidator.validate(sql);

        // ── 5. Execute ───────────────────────────────────────────────────────
        try {
            List<Map<String, Object>> rows    = jdbcTemplate.queryForList(sql);
            List<String>              columns = rows.isEmpty()
                    ? List.of()
                    : new ArrayList<>(rows.get(0).keySet());
            long durationMs = System.currentTimeMillis() - start;
            log.info("Text-to-SQL executed in {}ms — {} rows", durationMs, rows.size());
            return new SqlQueryResponse(sql, columns, rows, rows.size(), null, null, durationMs);

        } catch (Exception e) {
            log.warn("Text-to-SQL execution error: {}", e.getMessage());
            return new SqlQueryResponse(sql, List.of(), List.of(), 0, null,
                    "Query execution failed: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    // ── package-visible for testing ───────────────────────────────────────────

    String extractSql(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        // Remove markdown code fences (```sql ... ``` or ``` ... ```)
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:sql)?\\s*\\n?", "")
                 .replaceFirst("\\n?```\\s*$", "")
                 .strip();
        }
        return s;
    }

    boolean looksLikeSql(String sql) {
        if (sql == null || sql.isBlank()) return false;
        String upper = sql.stripLeading().toUpperCase();
        return upper.startsWith("SELECT")   || upper.startsWith("WITH ")    ||
               upper.startsWith("INSERT")   || upper.startsWith("UPDATE")   ||
               upper.startsWith("DELETE")   || upper.startsWith("DROP")     ||
               upper.startsWith("CREATE")   || upper.startsWith("ALTER")    ||
               upper.startsWith("TRUNCATE");
    }

    private String buildPrompt(String question, String schema, String language) {
        String langHint = "en".equalsIgnoreCase(language)
                ? "Respond in English."
                : "Respond in the language: " + language + ".";
        return """
                You are a PostgreSQL expert. Given the database schema and a natural language question, \
                generate a valid SQL SELECT query.
                If the question is ambiguous or cannot be answered with a SELECT, ask for clarification \
                instead of generating SQL.

                DATABASE SCHEMA:
                %s

                RULES:
                - Return ONLY the SQL SELECT query, no explanation or markdown
                - Only generate SELECT queries (never INSERT/UPDATE/DELETE/DDL)
                - Do not use semicolons
                - Query only tables shown in the schema
                - Status/enum values in this database are UPPERCASE (e.g. 'APPROVED', 'OPEN', 'SUBMITTED', 'UNDER_REVIEW', 'REJECTED')
                - Use ILIKE for case-insensitive text matching when filtering by user-provided text
                - %s

                QUESTION: %s

                SQL:""".formatted(schema, langHint, question);
    }
}
