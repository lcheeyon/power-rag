package com.powerrag.rag;

import com.powerrag.domain.DocumentChunk;
import com.powerrag.domain.DocumentChunkRepository;
import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.retrieval.HybridRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HybridRetriever Unit Tests")
class HybridRetrieverTest {

    @Mock VectorStore             vectorStore;
    @Mock DocumentChunkRepository chunkRepository;

    HybridRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new HybridRetriever(vectorStore, chunkRepository, 5);
    }

    private org.springframework.ai.document.Document aiDoc(String id, String text) {
        return new org.springframework.ai.document.Document(
                id, text,
                Map.of("file_name", "test.pdf", "document_id", UUID.randomUUID().toString()));
    }

    private DocumentChunk domainChunk(String qdrantId, String text) {
        com.powerrag.domain.Document doc = com.powerrag.domain.Document.builder()
                .id(UUID.randomUUID()).fileName("test.java")
                .fileType(com.powerrag.domain.Document.FileType.JAVA)
                .fileSize(100).status(com.powerrag.domain.Document.Status.INDEXED).build();
        DocumentChunk c = new DocumentChunk();
        c.setQdrantId(qdrantId);
        c.setChunkText(text);
        c.setDocument(doc);
        c.setMetadata(Map.of("file_name", "test.java"));
        return c;
    }

    @Test
    @DisplayName("Dense-only results returned when FTS is empty")
    void denseOnly_whenFtsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(aiDoc("id1", "dense text")));
        when(chunkRepository.fullTextSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievedChunk> results = retriever.retrieve("test query");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).qdrantId()).isEqualTo("id1");
    }

    @Test
    @DisplayName("FTS-only results returned when dense is empty")
    void ftsOnly_whenDenseEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chunkRepository.fullTextSearch(anyString(), anyInt()))
                .thenReturn(List.of(domainChunk("qid1", "keyword text")));

        List<RetrievedChunk> results = retriever.retrieve("keyword query");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).qdrantId()).isEqualTo("qid1");
    }

    @Test
    @DisplayName("RRF merges overlapping results: shared ID gets higher score")
    void overlapping_idsGetHigherRrfScore() {
        String sharedId = "shared-id";
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(aiDoc(sharedId, "shared text"), aiDoc("only-dense", "dense only")));
        when(chunkRepository.fullTextSearch(anyString(), anyInt()))
                .thenReturn(List.of(domainChunk(sharedId, "shared text"), domainChunk("only-fts", "fts only")));

        List<RetrievedChunk> results = retriever.retrieve("query");

        // shared ID should be ranked first (highest combined RRF score)
        assertThat(results.get(0).qdrantId()).isEqualTo(sharedId);
        double singleListScore = 1.0 / (1 + HybridRetriever.RRF_K);
        assertThat(results.get(0).score()).isGreaterThan(singleListScore);
    }

    @Test
    @DisplayName("Result count is capped at topK")
    void results_cappedAtTopK() {
        var docs = List.of(
                aiDoc("a", "t"), aiDoc("b", "t"), aiDoc("c", "t"),
                aiDoc("d", "t"), aiDoc("e", "t"), aiDoc("f", "t"),
                aiDoc("g", "t"), aiDoc("h", "t"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);
        when(chunkRepository.fullTextSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievedChunk> results = retriever.retrieve("query");

        assertThat(results).hasSizeLessThanOrEqualTo(5);  // topK=5
    }

    @Test
    @DisplayName("Blank query returns empty list without calling stores")
    void blankQuery_returnsEmpty() {
        assertThat(retriever.retrieve("   ")).isEmpty();
        assertThat(retriever.retrieve(null)).isEmpty();
    }

    @Test
    @DisplayName("Both sources empty returns empty list")
    void bothEmpty_returnsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chunkRepository.fullTextSearch(anyString(), anyInt())).thenReturn(List.of());

        assertThat(retriever.retrieve("anything")).isEmpty();
    }
}
