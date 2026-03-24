package com.powerrag.rag.model;

import java.util.Map;

/**
 * A document chunk returned by the HybridRetriever after RRF merge.
 * The {@code score} is an RRF-merged rank score (not a raw cosine similarity).
 */
public record RetrievedChunk(
        String qdrantId,
        String text,
        double score,
        Map<String, Object> metadata
) {}
