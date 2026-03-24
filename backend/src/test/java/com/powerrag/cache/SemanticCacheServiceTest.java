package com.powerrag.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerrag.rag.model.SourceRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticCacheService Unit Tests")
class SemanticCacheServiceTest {

    @Mock VectorStore vectorStore;

    SemanticCacheService cacheService;

    private static final double THRESHOLD    = 0.92;
    private static final long   TTL_SECONDS  = 86400;
    private static final long   TTL_MS       = TTL_SECONDS * 1000L;

    @BeforeEach
    void setUp() {
        cacheService = new SemanticCacheService(vectorStore, new ObjectMapper(), THRESHOLD, TTL_SECONDS);
    }

    // ── lookup ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lookup returns empty when no similarity match found")
    void lookup_noMatch_returnsEmpty() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of());

        assertThat(cacheService.lookup("What is RAG?", "en")).isEmpty();
    }

    @Test
    @DisplayName("lookup returns CacheHit with correct fields on match")
    void lookup_validMatch_returnsCacheHit() {
        Document doc = new Document("What is RAG?", Map.of(
                "answer",     "RAG is Retrieval Augmented Generation.",
                "confidence", 0.9,
                "model_id",   "claude-sonnet-4-6",
                "sources",    "[]",
                "cached_at",  (double) System.currentTimeMillis()
        ));
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        Optional<CacheHit> result = cacheService.lookup("What is RAG?", "en");

        assertThat(result).isPresent();
        assertThat(result.get().answer()).isEqualTo("RAG is Retrieval Augmented Generation.");
        assertThat(result.get().confidence()).isEqualTo(0.9);
        assertThat(result.get().modelId()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    @DisplayName("lookup returns empty and deletes document when TTL is exceeded")
    void lookup_expired_returnsEmptyAndDeletes() {
        long expiredAt = System.currentTimeMillis() - TTL_MS - 1000L; // 1s past TTL
        Document doc = new Document("old question", Map.of(
                "answer",     "old answer",
                "confidence", 0.8,
                "model_id",   "model",
                "sources",    "[]",
                "cached_at",  (double) expiredAt
        ));
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(doc));

        Optional<CacheHit> result = cacheService.lookup("old question", "en");

        assertThat(result).isEmpty();
        verify(vectorStore).delete(List.of(doc.getId()));
    }

    @Test
    @DisplayName("lookup returns empty and does not throw on VectorStore exception")
    void lookup_exception_returnsEmptyGracefully() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenThrow(new RuntimeException("Redis down"));

        assertThatNoException().isThrownBy(() -> cacheService.lookup("q", "en"));
        assertThat(cacheService.lookup("q", "en")).isEmpty();
    }

    // ── store ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("store calls vectorStore.add with correct metadata fields")
    void store_callsVectorStoreAdd_withMetadata() {
        List<SourceRef> sources = List.of(new SourceRef("file.pdf", "page-1", "snippet", 1, null, null));

        cacheService.store("What is RAG?", "en", "RAG is great.", 0.85, sources, "claude");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document stored = captor.getValue().get(0);
        assertThat(stored.getMetadata()).containsEntry("answer", "RAG is great.");
        assertThat(stored.getMetadata()).containsEntry("language", "en");
        assertThat(stored.getMetadata()).containsKey("cached_at");
        assertThat(stored.getMetadata().get("sources").toString()).contains("file.pdf");
    }

    @Test
    @DisplayName("store does not propagate VectorStore exception")
    void store_exception_doesNotPropagate() {
        doThrow(new RuntimeException("Redis down")).when(vectorStore).add(anyList());

        assertThatNoException().isThrownBy(
                () -> cacheService.store("q", "en", "a", 0.9, List.of(), "model"));
    }
}
