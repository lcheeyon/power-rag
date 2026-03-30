package com.powerrag.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for POST /api/chat/query.
 */
public record ChatQueryRequest(
        @NotBlank(message = "question must not be blank") String question,
        UUID sessionId,
        String language,
        String modelProvider,
        String modelId,
        String imageBase64,   // optional data URL, e.g. "data:image/png;base64,..."
        /** IANA timezone from the browser ({@code Intl...resolvedOptions().timeZone}) for {@code get_current_time}. */
        @Size(max = 120) String clientTimezone
) {}
