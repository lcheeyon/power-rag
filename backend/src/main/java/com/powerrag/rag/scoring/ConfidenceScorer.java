package com.powerrag.rag.scoring;

import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.retrieval.HybridRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts RRF-merged scores into a normalised [0, 1] confidence value.
 *
 * <p>The theoretical maximum RRF score is {@code 2/(1+k)} when a chunk
 * is ranked first in both the dense and keyword lists (RRF k=60).
 */
@Component
public class ConfidenceScorer {

    private static final double MAX_RRF = 2.0 / (1.0 + HybridRetriever.RRF_K);

    public double score(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0.0;
        double raw = chunks.get(0).score() / MAX_RRF;
        return Math.round(Math.min(1.0, Math.max(0.0, raw)) * 10_000.0) / 10_000.0;
    }
}
