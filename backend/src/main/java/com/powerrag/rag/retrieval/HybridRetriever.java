package com.powerrag.rag.retrieval;

import com.powerrag.domain.DocumentChunk;
import com.powerrag.domain.DocumentChunkRepository;
import com.powerrag.rag.model.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Hybrid retriever combining dense vector search (Qdrant) and keyword FTS (PostgreSQL).
 * Results are merged with Reciprocal Rank Fusion (RRF, k=60).
 */
@Slf4j
@Component
public class HybridRetriever {

    public static final double RRF_K = 60.0;

    private final VectorStore    vectorStore;
    private final DocumentChunkRepository chunkRepository;
    private final int topK;

    public HybridRetriever(@Qualifier("vectorStore") VectorStore vectorStore,
                           DocumentChunkRepository chunkRepository,
                           @Value("${powerrag.rag.top-k:10}") int topK) {
        this.vectorStore     = vectorStore;
        this.chunkRepository = chunkRepository;
        this.topK            = topK;
    }

    public List<RetrievedChunk> retrieve(String query) {
        if (query == null || query.isBlank()) return List.of();

        // ── Dense (Qdrant) ──────────────────────────────────────────────────
        List<Document> dense;
        try {
            dense = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK * 2).build());
        } catch (Exception e) {
            log.warn("Dense search failed: {}", e.getMessage());
            dense = List.of();
        }

        // ── Keyword (PostgreSQL FTS) ─────────────────────────────────────────
        List<DocumentChunk> keyword;
        try {
            keyword = chunkRepository.fullTextSearch(query, topK * 2);
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
            keyword = List.of();
        }

        return rrfMerge(dense, keyword, topK);
    }

    // ── RRF merge ─────────────────────────────────────────────────────────

    private List<RetrievedChunk> rrfMerge(List<Document> dense,
                                          List<DocumentChunk> keyword,
                                          int k) {
        Map<String, Double>        scores   = new LinkedHashMap<>();
        Map<String, RetrievedChunk> chunkMap = new LinkedHashMap<>();

        // Score dense results by rank position
        for (int i = 0; i < dense.size(); i++) {
            Document doc = dense.get(i);
            String id = doc.getId();
            scores.merge(id, 1.0 / (i + 1 + RRF_K), Double::sum);
            chunkMap.computeIfAbsent(id, _ -> fromAiDocument(doc));
        }

        // Score FTS results by rank position
        for (int i = 0; i < keyword.size(); i++) {
            DocumentChunk chunk = keyword.get(i);
            String id = chunk.getQdrantId();
            scores.merge(id, 1.0 / (i + 1 + RRF_K), Double::sum);
            chunkMap.computeIfAbsent(id, _ -> fromDomainChunk(chunk));
        }

        // Sort by RRF score descending, cap at topK
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .map(e -> withScore(chunkMap.get(e.getKey()), e.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    // ── Adapters ──────────────────────────────────────────────────────────

    private RetrievedChunk fromAiDocument(Document doc) {
        return new RetrievedChunk(doc.getId(), doc.getText(),
                0.0, new HashMap<>(doc.getMetadata()));
    }

    private RetrievedChunk fromDomainChunk(DocumentChunk chunk) {
        Map<String, Object> meta = new HashMap<>(
                chunk.getMetadata() != null ? chunk.getMetadata() : Map.of());
        meta.put("document_id", chunk.getDocument().getId().toString());
        return new RetrievedChunk(chunk.getQdrantId(), chunk.getChunkText(),
                0.0, meta);
    }

    private RetrievedChunk withScore(RetrievedChunk c, double score) {
        if (c == null) return null;
        return new RetrievedChunk(c.qdrantId(), c.text(), score, c.metadata());
    }
}
