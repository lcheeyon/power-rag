package com.powerrag.multilingual;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/user/preferences.
 */
public record UserPreferencesRequest(
        @NotBlank(message = "preferredLanguage must not be blank")
        String preferredLanguage
) {}
