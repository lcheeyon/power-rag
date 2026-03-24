package com.powerrag.cache;

import com.powerrag.infrastructure.TestContainersConfig;
import com.powerrag.rag.model.SourceRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for SemanticCacheService against a real Redis Stack container.
 * The semantic cache is enabled via a test property override.
 * OllamaEmbeddingModel is replaced with a mock that returns fixed 4-dim vectors.
 * Tests use unique language tags per run for data isolation without flushing Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@TestPropertySource(properties = "powerrag.semantic-cache.enabled=true")
@DisplayName("SemanticCache Integration Tests")
class SemanticCacheIntegrationTest {

    private static final float[] FIXED_EMBEDDING = {0.1f, 0.2f, 0.3f, 0.4f};

    /**
     * Provides a pre-configured mock EmbeddingModel that returns fixed vectors.
     * @Primary ensures it wins over the auto-configured OllamaEmbeddingModel.
     * The mock is fully configured here so it is ready when RedisVectorStore
     * calls embeddingModel.dimensions() during afterPropertiesSet().
     */
    @TestConfiguration
    static class MockEmbeddingConfig {
        @Bean
        @Primary
        OllamaEmbeddingModel mockOllamaEmbeddingModel() {
            OllamaEmbeddingModel mockModel = mock(OllamaEmbeddingModel.class);
            // dimensions() is called by RedisVectorStore during afterPropertiesSet() to create the HNSW index
            when(mockModel.dimensions()).thenReturn(FIXED_EMBEDDING.length);
            // embed(String) is called by RedisVectorStore.similaritySearch() for the query
            when(mockModel.embed(anyString())).thenReturn(FIXED_EMBEDDING);
            // embed(Document) is used internally by some embed paths
            when(mockModel.embed(any(org.springframework.ai.document.Document.class)))
                    .thenReturn(FIXED_EMBEDDING);
            // embed(List<Document>, EmbeddingOptions, BatchingStrategy) is called by RedisVectorStore.doAdd()
            // and returns List<float[]> — one entry per document
            when(mockModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                    .thenAnswer(inv -> {
                        List<org.springframework.ai.document.Document> docs = inv.getArgument(0);
                        return docs.stream().map(d -> FIXED_EMBEDDING).toList();
                    });
            when(mockModel.call(any(EmbeddingRequest.class))).thenAnswer(inv -> {
                EmbeddingRequest req = inv.getArgument(0);
                int n = req.getInstructions().size();
                List<Embedding> embeddings = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    embeddings.add(new Embedding(FIXED_EMBEDDING, i));
                }
                return new EmbeddingResponse(embeddings);
            });
            return mockModel;
        }
    }

    @Autowired
    SemanticCache semanticCache;

    /** Unique language prefix for each test — avoids Redis data bleed between tests. */
    private String testLang;

    @BeforeEach
    void setUp() {
        testLang = "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Cache miss: lookup on an unseen language prefix returns empty")
    void cacheMiss_returnsEmpty() {
        assertThat(semanticCache.lookup("What is RAG?", testLang)).isEmpty();
    }

    @Test
    @DisplayName("Store then lookup returns the cached answer")
    void storeAndLookup_returnsCachedAnswer() {
        String answer = "RAG is Retrieval Augmented Generation.";

        semanticCache.store("What is RAG?", testLang, answer, 0.9,
                List.of(new SourceRef("doc.pdf", "page-1", "snippet", 1, null, null)), "claude-sonnet-4-6");

        Optional<CacheHit> hit = semanticCache.lookup("What is RAG?", testLang);

        assertThat(hit).isPresent();
        assertThat(hit.get().answer()).isEqualTo(answer);
        assertThat(hit.get().confidence()).isEqualTo(0.9);
        assertThat(hit.get().modelId()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    @DisplayName("Different language tag returns a miss even for same question")
    void differentLanguage_returnssMiss() {
        String langEn = testLang + "-en";
        String langZh = testLang + "-zh";

        semanticCache.store("What is RAG?", langEn,
                "Answer in English", 0.9, List.of(), "claude");

        assertThat(semanticCache.lookup("What is RAG?", langZh)).isEmpty();
    }
}
