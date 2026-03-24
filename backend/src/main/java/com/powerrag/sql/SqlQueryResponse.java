package com.powerrag.sql;

import java.util.List;
import java.util.Map;

/**
 * Response from a Text-to-SQL query.
 * <ul>
 *   <li>{@code sql} — the generated SELECT query (null on clarification)</li>
 *   <li>{@code clarification} — non-null when the LLM requested more detail instead of SQL</li>
 *   <li>{@code executionError} — non-null when SQL execution failed</li>
 * </ul>
 */
public record SqlQueryResponse(
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        String clarification,
        String executionError,
        long durationMs
) {}
