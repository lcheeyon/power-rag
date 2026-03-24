package com.powerrag.sql;

import com.powerrag.sql.exception.SqlValidationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates that a SQL string is a safe, read-only SELECT query.
 * Rejects all non-SELECT statements including INSERT, UPDATE, DELETE, DDL, and multi-statement queries.
 */
@Component
public class SqlValidator {

    /** Matches DML / DDL keywords at the start of a statement or after a semicolon. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE|MERGE|EXEC(?:UTE)?|CALL|DO|COPY)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Validates the given SQL.
     *
     * @throws SqlValidationException if the SQL is blank, does not start with SELECT,
     *                                 contains forbidden DML/DDL keywords, or is multi-statement.
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlValidationException("SQL query is empty");
        }

        String trimmed = sql.stripLeading();

        if (!trimmed.substring(0, Math.min(6, trimmed.length()))
                    .equalsIgnoreCase("SELECT") &&
            !trimmed.toUpperCase().startsWith("WITH ")) {
            throw new SqlValidationException(
                    "Only SELECT queries are allowed. Received: "
                    + trimmed.substring(0, Math.min(30, trimmed.length())));
        }

        // Strip trailing semicolons then reject any remaining semicolons (multi-statement)
        String withoutTrailingSemi = trimmed.stripTrailing();
        if (withoutTrailingSemi.endsWith(";")) {
            withoutTrailingSemi = withoutTrailingSemi.substring(0, withoutTrailingSemi.length() - 1);
        }
        if (withoutTrailingSemi.contains(";")) {
            throw new SqlValidationException("Multi-statement queries are not allowed");
        }

        // Check for forbidden DML/DDL keywords
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new SqlValidationException(
                    "Query contains a forbidden SQL operation");
        }
    }
}
