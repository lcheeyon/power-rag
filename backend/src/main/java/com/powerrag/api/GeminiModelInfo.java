package com.powerrag.api;

/**
 * One Gemini model row from {@link GeminiModelCatalogService} (Google {@code models.list}).
 */
public record GeminiModelInfo(
        String modelId,
        String displayName,
        String description,
        boolean multimodal
) {}
