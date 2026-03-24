package com.powerrag.sql.exception;

/**
 * Thrown when a SQL query fails validation (e.g. non-SELECT statement).
 */
public class SqlValidationException extends RuntimeException {

    public SqlValidationException(String message) {
        super(message);
    }
}
