package com.powerrag.rag;

import com.powerrag.domain.InteractionRepository;
import com.powerrag.infrastructure.TestContainersConfig;
import com.powerrag.rag.model.RagResponse;
import com.powerrag.rag.service.RagService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import com.powerrag.ingestion.service.DocumentIngestionService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end RAG integration test.
 * Ingests a real document into PostgreSQL, then queries via RagService.
 * AnthropicChatModel is mocked to avoid real API calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("RAG Integration Tests")
class RagIntegrationTest {

    @Autowired RagService              ragService;
    @Autowired DocumentIngestionService ingestionService;
    @Autowired InteractionRepository   interactionRepository;

    @MockitoBean AnthropicChatModel   anthropicChatModel;
    /** Mocked so GuardrailService never calls the real Gemini API for input safety. */
    @MockitoBean GoogleGenAiChatModel googleGenAiChatModel;
    @MockitoBean OllamaChatModel      ollamaChatModel;

    @BeforeEach
    void configureMock() {
        // Input guardrail (Gemini): classify all input as safe
        when(googleGenAiChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("safe")))));
        // LLM model: return a canned answer
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "The greet method returns a greeting string. [SOURCE 1]")))));
    }

    @BeforeEach
    void ingestDocument() {
        String javaSource = """
                package com.example;
                public class Greeter {
                    public String greet(String name) { return "Hello, " + name; }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "Greeter.java", "text/plain",
                javaSource.getBytes(StandardCharsets.UTF_8));
        ingestionService.ingest(file, "integration test", null);
    }

    @Test
    @DisplayName("Query returns mocked LLM answer with interaction saved")
    void query_returnsAnswerAndSavesInteraction() {
        long countBefore = interactionRepository.count();

        RagResponse response = ragService.query(
                "What does the greet method do?", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.answer()).contains("greet");
        assertThat(response.interactionId()).isNotNull();
        assertThat(interactionRepository.count()).isGreaterThan(countBefore);
    }

    @Test
    @DisplayName("Response always has non-negative durationMs")
    void query_durationMsIsNonNegative() {
        RagResponse response = ragService.query("Any question?", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Interaction is persisted with correct modelProvider")
    void interaction_persistedWithModelProvider() {
        ragService.query("Test query", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        var interactions = interactionRepository.findAll();
        assertThat(interactions).isNotEmpty();
        assertThat(interactions.get(0).getModelProvider()).isEqualTo("ANTHROPIC");
    }
}
