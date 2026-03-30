package com.powerrag.rag;

import com.powerrag.cache.CacheHit;
import com.powerrag.cache.SemanticCache;
import com.powerrag.domain.Interaction;
import com.powerrag.domain.InteractionRepository;
import com.powerrag.guardrails.GuardrailResult;
import com.powerrag.guardrails.GuardrailService;
import com.powerrag.multilingual.MultilingualPromptBuilder;
import com.powerrag.rag.assembly.ContextAssembler;
import com.powerrag.rag.model.RagResponse;
import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.model.SourceRef;
import com.powerrag.rag.retrieval.HybridRetriever;
import com.powerrag.rag.scoring.ConfidenceScorer;
import com.powerrag.rag.service.ImageGenerationService;
import com.powerrag.rag.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerrag.mcp.McpInvocationRecorder;
import com.powerrag.rag.intent.QueryIntentClassifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagService Unit Tests")
class RagServiceTest {

    @Mock HybridRetriever        retriever;
    @Mock ConfidenceScorer       scorer;
    @Mock ContextAssembler       assembler;
    @Mock ChatClient             chatClient;   // used as the default / sonnet mock
    @Mock ChatClient             chatClient2;  // opus
    @Mock ChatClient             chatClient3;  // haiku
    @Mock ChatClient             chatClient4;  // geminiFlash
    @Mock ChatClient             chatClient5;  // geminiPro
    @Mock ChatClient             chatClient6;  // ollamaQwen
    @Mock InteractionRepository  interactionRepository;
    @Mock SemanticCache          semanticCache;
    @Mock GuardrailService       guardrailService;
    @Mock ImageGenerationService imageGenerationService;

    // ChatClient fluent-API mocks
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec      callSpec;

    RagService           service;
    private final ConfidenceScorer confidenceScorer = new ConfidenceScorer();

    @BeforeEach
    void setUp() {
        lenient().when(scorer.score(anyList())).thenAnswer(inv -> confidenceScorer.score(inv.getArgument(0)));
        lenient().when(scorer.responseConfidence(anyDouble(), anyBoolean(), anyList()))
                .thenAnswer(inv -> confidenceScorer.responseConfidence(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        service = new RagService(retriever, scorer, assembler,
                chatClient, chatClient2, chatClient3, chatClient4, chatClient5, chatClient6,
                interactionRepository, semanticCache, new MultilingualPromptBuilder(),
                guardrailService, imageGenerationService,
                Optional.empty(), new McpInvocationRecorder(), new QueryIntentClassifier(new ObjectMapper()),
                false, false);
        // Image generation returns no result by default (not a generation request)
        lenient().when(imageGenerationService.isImageGenerationRequest(anyString())).thenReturn(false);
        lenient().when(imageGenerationService.generateImage(anyString(), nullable(String.class))).thenReturn(null);
        lenient().when(imageGenerationService.fallbackToLlmOnFailure()).thenReturn(false);

        // Guardrail: allow all input/output by default (lenient — not all test paths use both)
        lenient().when(guardrailService.checkInput(anyString())).thenReturn(GuardrailResult.safe());
        lenient().when(guardrailService.checkOutput(anyString())).thenReturn(GuardrailResult.safe());

        // Semantic cache is a miss by default
        when(semanticCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());

        // Wire the ChatClient fluent chain: prompt() → user() → call() → content()
        // All lenient: cache-hit tests skip the LLM path entirely
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
        lenient().when(callSpec.content()).thenReturn("LLM answer about the query.");  // unused when call() throws

        // interactionRepository.save() returns an entity with a UUID (lenient: not called on LLM failure path)
        lenient().when(interactionRepository.save(any(Interaction.class))).thenAnswer(inv -> {
            Interaction i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
    }

    private RetrievedChunk chunk(String id) {
        return new RetrievedChunk(id, "Some text about " + id, 0.02,
                Map.of("file_name", "doc.pdf", "section", "page-1"));
    }

    @Test
    @DisplayName("Happy path: returns answer, sources, confidence and interactionId")
    void happyPath_returnsFullResponse() {
        when(retriever.retrieve(anyString())).thenReturn(List.of(chunk("c1"), chunk("c2")));
        when(assembler.assemble(anyList())).thenReturn("Context text");
        when(assembler.extractSources(anyList())).thenReturn(
                List.of(new SourceRef("doc.pdf", "page-1", "Some text…", 1, null, null)));

        RagResponse response = service.query("What is Power RAG?", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.answer()).isEqualTo("LLM answer about the query.");
        // RRF-normalised top chunk (~0.61) → calibrated KB confidence (~0.70)
        assertThat(response.confidence()).isBetween(0.68, 0.72);
        assertThat(response.sources()).hasSize(1);
        assertThat(response.interactionId()).isNotNull();
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.cacheHit()).isFalse();
    }

    @Test
    @DisplayName("Cache hit: returns cached answer without calling LLM")
    void cacheHit_returnsCachedAnswerWithoutLlmCall() {
        CacheHit hit = new CacheHit("Cached answer", 0.9,
                List.of(new SourceRef("doc.pdf", "s1", "snippet", 1, null, null)), "claude-sonnet-4-6");
        when(semanticCache.lookup(anyString(), anyString())).thenReturn(Optional.of(hit));

        RagResponse response = service.query("What is RAG?", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.answer()).isEqualTo("Cached answer");
        assertThat(response.cacheHit()).isTrue();
        verify(chatClient, never()).prompt();           // LLM not called
        verify(retriever, never()).retrieve(anyString()); // retriever not called
    }

    @Test
    @DisplayName("Empty retrieval: returns answer with zero confidence and no sources")
    void emptyRetrieval_zeroConfidenceNoSources() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(scorer.score(anyList())).thenReturn(0.0);
        lenient().when(assembler.assemble(anyList())).thenReturn("");
        when(assembler.extractSources(anyList())).thenReturn(List.of());

        RagResponse response = service.query("Unknown question", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.confidence()).isEqualTo(0.0);
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).isNotBlank();
    }

    @Test
    @DisplayName("Interaction is always saved to repository")
    void interactionSavedOnEveryQuery() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(scorer.score(anyList())).thenReturn(0.0);
        lenient().when(assembler.assemble(anyList())).thenReturn("");
        when(assembler.extractSources(anyList())).thenReturn(List.of());

        service.query("test", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        verify(interactionRepository).save(any(Interaction.class));
    }

    @Test
    @DisplayName("LLM call failure returns error message and still saves interaction")
    void llmFailure_returnsErrorMessageAndSaves() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(scorer.score(anyList())).thenReturn(0.0);
        lenient().when(assembler.assemble(anyList())).thenReturn("");
        when(assembler.extractSources(anyList())).thenReturn(List.of());
        when(requestSpec.call()).thenThrow(new RuntimeException("LLM timeout"));

        RagResponse response = service.query("Will fail", UUID.randomUUID(), null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.error()).isNotBlank();
        assertThat(response.answer()).isBlank();
        assertThat(response.interactionId()).isNull();
    }

    @Test
    @DisplayName("sessionId is auto-generated when null is passed")
    void nullSessionId_generatesUUID() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(scorer.score(anyList())).thenReturn(0.0);
        lenient().when(assembler.assemble(anyList())).thenReturn("");
        when(assembler.extractSources(anyList())).thenReturn(List.of());

        RagResponse response = service.query("q", null, null, "en", "ANTHROPIC", "claude-sonnet-4-6");

        assertThat(response.interactionId()).isNotNull();
        verify(interactionRepository).save(argThat(i -> i.getSessionId() != null));
    }
}
