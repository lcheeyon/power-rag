package com.powerrag.rag;

import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.retrieval.HybridRetriever;
import com.powerrag.rag.scoring.ConfidenceScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfidenceScorer Unit Tests")
class ConfidenceScorerTest {

    private final ConfidenceScorer scorer = new ConfidenceScorer();

    private static final double MAX_RRF = 2.0 / (1.0 + HybridRetriever.RRF_K);

    private RetrievedChunk chunk(double score) {
        return new RetrievedChunk("id", "text", score, Map.of());
    }

    @Test
    @DisplayName("Empty list returns 0.0")
    void emptyChunks_returnsZero() {
        assertThat(scorer.score(List.of())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Null list returns 0.0")
    void nullChunks_returnsZero() {
        assertThat(scorer.score(null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Max RRF score normalises to 1.0")
    void maxRrfScore_returnsOne() {
        double confidence = scorer.score(List.of(chunk(MAX_RRF)));
        assertThat(confidence).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Half max RRF score normalises to 0.5")
    void halfMaxScore_returnsHalf() {
        double confidence = scorer.score(List.of(chunk(MAX_RRF / 2)));
        assertThat(confidence).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Score is clamped to [0, 1]")
    void scoreAboveMax_clampedToOne() {
        double confidence = scorer.score(List.of(chunk(MAX_RRF * 10)));
        assertThat(confidence).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Only top chunk determines score; lower chunks ignored")
    void onlyTopChunkIsUsed() {
        double topScore = MAX_RRF;
        double lowScore = MAX_RRF * 0.1;
        double expected = scorer.score(List.of(chunk(topScore)));
        double actual   = scorer.score(List.of(chunk(topScore), chunk(lowScore)));
        assertThat(actual).isEqualTo(expected);
    }
}
