package com.powerrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for all LLM model IDs.
 * Values are set in application.yml and can be overridden per profile.
 */
@ConfigurationProperties(prefix = "powerrag.models")
public record ModelProperties(
        ClaudeModels claude,
        GeminiModels gemini,
        OllamaModels ollama,
        CacheProperties cache
) {

    public record ClaudeModels(
            String powerful,   // claude-opus-4-6
            String balanced,   // claude-sonnet-4-6  (default)
            String fast        // claude-haiku-4-5-20251001
    ) {}

    public record GeminiModels(
            String powerful,   // gemini-2.5-pro
            String balanced,   // gemini-2.5-flash   (default)
            String fast,       // gemini-2.5-flash-lite
            String preview     // gemini-3.1-pro-preview
    ) {}

    public record OllamaModels(
            String coder,      // qwen2.5-coder:32b
            String deepCoder,  // deepseek-coder-v2:16b
            String guard       // llama-guard3:8b
    ) {}

    public record CacheProperties(
            double similarityThreshold,  // default 0.92
            long   ttlSeconds            // default 86400 (24h)
    ) {}
}
