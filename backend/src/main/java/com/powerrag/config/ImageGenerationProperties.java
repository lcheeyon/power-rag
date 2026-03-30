package com.powerrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Google Gemini / Imagen models used for native image generation (Nano Banana family + fallbacks).
 */
@ConfigurationProperties(prefix = "powerrag.image-generation")
public record ImageGenerationProperties(
        List<String> geminiNativeModels,
        String imagenModel,
        String legacyGeminiModel,
        boolean fallbackToLlmOnFailure
) {

    public ImageGenerationProperties {
        if (geminiNativeModels == null || geminiNativeModels.isEmpty()) {
            geminiNativeModels = List.of(
                    "gemini-3-pro-image-preview",
                    "gemini-3.1-flash-image-preview",
                    "gemini-2.5-flash-image"
            );
        }
        if (imagenModel == null || imagenModel.isBlank()) {
            imagenModel = "imagen-3.0-generate-002";
        }
        if (legacyGeminiModel == null || legacyGeminiModel.isBlank()) {
            legacyGeminiModel = "gemini-2.0-flash-preview-image-generation";
        }
    }
}
