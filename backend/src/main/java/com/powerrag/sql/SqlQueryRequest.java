package com.powerrag.sql;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/sql/query.
 */
public record SqlQueryRequest(
        @NotBlank(message = "question must not be blank") String question,
        String language
) {}
