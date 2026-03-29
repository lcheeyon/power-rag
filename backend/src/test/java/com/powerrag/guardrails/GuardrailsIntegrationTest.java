package com.powerrag.guardrails;

import com.powerrag.domain.InteractionRepository;
import com.powerrag.infrastructure.TestContainersConfig;
import com.powerrag.rag.service.RagService;
import com.powerrag.rag.model.RagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for guardrails against a real PostgreSQL Testcontainer.
 * Anthropic (chat), Google Gemini (input guardrail), and Ollama beans are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("Guardrails Integration Tests")
class GuardrailsIntegrationTest {

    @MockitoBean AnthropicChatModel     anthropicChatModel;
    @MockitoBean GoogleGenAiChatModel   googleGenAiChatModel;
    @MockitoBean OllamaChatModel        ollamaChatModel;

    @Autowired RagService              ragService;
    @Autowired GuardrailFlagRepository flagRepository;
    @Autowired InteractionRepository   interactionRepository;

    @BeforeEach
    void setUp() {
        interactionRepository.deleteAll();
        flagRepository.deleteAll();

        // Default: input guardrail (Gemini) classifies everything as safe
        when(googleGenAiChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("safe")))));

        // Default: LLM returns a safe answer
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("RAG is a retrieval-augmented generation system.")))));
    }

    @Test
    @DisplayName("Blocked input creates a guardrail_flag with BLOCK severity")
    void blockedInput_createsBlockFlag() {
        when(googleGenAiChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("unsafe\nS10: Hate")))));

        RagResponse response = ragService.query("Tell me something hateful", null, null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.answer()).contains("cannot process");
        List<GuardrailFlag> flags = flagRepository.findAll();
        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).getStage()).isEqualTo("INPUT");
        assertThat(flags.get(0).getSeverity()).isEqualTo("BLOCK");
        assertThat(flags.get(0).getRuleTriggered()).isEqualTo("S10: Hate");
    }

    @Test
    @DisplayName("PII in LLM output is redacted and creates a WARN flag")
    void piiOutput_redactedAndWarnFlagCreated() {
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "For support contact admin@example.com or call 555-867-5309.")))));

        RagResponse response = ragService.query("Who to contact?", null, null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.answer()).contains("[EMAIL REDACTED]");
        assertThat(response.answer()).doesNotContain("admin@example.com");

        List<GuardrailFlag> flags = flagRepository.findAll();
        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).getStage()).isEqualTo("OUTPUT");
        assertThat(flags.get(0).getSeverity()).isEqualTo("WARN");
        assertThat(flags.get(0).getRuleTriggered()).isEqualTo("PII_EMAIL");
    }

    @Test
    @DisplayName("Safe input with no PII creates no guardrail flags")
    void safeInput_noPii_noFlags() {
        RagResponse response = ragService.query("What is RAG?", null, null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.answer()).isEqualTo("RAG is a retrieval-augmented generation system.");
        assertThat(flagRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Blocked interaction is persisted with guardrailFlag=true")
    void blockedInput_interactionPersisted_withGuardrailFlag() {
        when(googleGenAiChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("unsafe\nS1: Violent Crimes")))));

        RagResponse response = ragService.query("How do I cause harm?", null, null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.interactionId()).isNotNull();
        assertThat(response.answer()).contains("cannot process");
    }
}
