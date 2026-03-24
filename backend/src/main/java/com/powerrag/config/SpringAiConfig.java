package com.powerrag.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registers ChatClient beans for each LLM provider.
 * The @Primary bean (Claude Sonnet) is injected by default where no qualifier is specified.
 */
@Configuration
public class SpringAiConfig {

    private static final String SYSTEM_PREAMBLE = """
            You are a knowledgeable, helpful, and fair assistant for the Power RAG system.
            Always be factual, unbiased, and respectful. Do not produce discriminatory,
            harmful, or misleading content. Always cite source documents when referencing
            technical data. Respond only in the language specified in the request.
            """;

    // ── Anthropic Claude ───────────────────────────────────────────────────

    @Bean
    @Primary
    @Qualifier("claudeSonnet")
    public ChatClient claudeSonnetClient(AnthropicChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    @Bean
    @Qualifier("claudeOpus")
    public ChatClient claudeOpusClient(AnthropicChatModel model) {
        // Model override applied at request time via options()
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    @Bean
    @Qualifier("claudeHaiku")
    public ChatClient claudeHaikuClient(AnthropicChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    // ── Google Gemini ──────────────────────────────────────────────────────

    @Bean
    @Qualifier("geminiFlash")
    public ChatClient geminiFlashClient(GoogleGenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    @Bean
    @Qualifier("geminiPro")
    public ChatClient geminiProClient(GoogleGenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    @Bean
    @Qualifier("geminiFlashLite")
    public ChatClient geminiFlashLiteClient(GoogleGenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    // ── Ollama ─────────────────────────────────────────────────────────────

    @Bean
    @Qualifier("ollamaQwen")
    public ChatClient ollamaQwenClient(OllamaChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    @Bean
    @Qualifier("ollamaDeepSeek")
    public ChatClient ollamaDeepSeekClient(OllamaChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PREAMBLE)
                .build();
    }

    /**
     * Dedicated client for llama-guard3:8b guardrail classification.
     * No system prompt — llama-guard uses its own classification format.
     * Model is overridden at call time via OllamaChatOptions.
     */
    @Bean
    @Qualifier("ollamaLlamaGuard")
    public ChatClient ollamaLlamaGuardClient(OllamaChatModel model) {
        return ChatClient.builder(model).build();
    }
}
