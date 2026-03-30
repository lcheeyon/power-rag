package com.powerrag.rag;

import com.powerrag.mcp.McpToolInvocationSummary;
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

    @Test
    @DisplayName("responseConfidence lifts typical KB retrieval into a higher band vs raw RRF ratio")
    void responseConfidence_kbCalibratedAboveRawRrf() {
        double raw = scorer.score(List.of(chunk(MAX_RRF * 0.35)));
        double combined = scorer.responseConfidence(raw, true, List.of());
        assertThat(combined).isGreaterThan(raw);
        assertThat(combined).isCloseTo(0.493, org.assertj.core.data.Offset.offset(0.02));
        assertThat(combined).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("responseConfidence adds bonus for successful fetch_url and GitHub tools")
    void responseConfidence_mcpBonusesStackWithKb() {
        double kbOnly = scorer.responseConfidence(0.5, true, List.of());
        var inv = List.of(
                new McpToolInvocationSummary("s", "powerrag-tools__fetch_url", true, 10L, null, null),
                new McpToolInvocationSummary("s", "powerrag-tools__github_search_code", true, 20L, null, null));
        double withTools = scorer.responseConfidence(0.5, true, inv);
        assertThat(withTools).isGreaterThan(kbOnly);
    }

    @Test
    @DisplayName("responseConfidence adds bonus for successful email_* tools (prefixed MCP connection id)")
    void responseConfidence_emailToolBonus() {
        double kbOnly = scorer.responseConfidence(0.5, true, List.of());
        var inv = List.of(
                new McpToolInvocationSummary("s", "boutquin-email__email_list", true, 5L, null, null));
        assertThat(scorer.responseConfidence(0.5, true, inv)).isGreaterThan(kbOnly);
    }

    @Test
    @DisplayName("responseConfidence uses MCP-only floor when KB unused but tools succeeded")
    void responseConfidence_mcpOnlyUsesFloor() {
        var weather = List.of(
                new McpToolInvocationSummary("s", "powerrag-tools__get_weather", true, 5L, null, null));
        assertThat(scorer.responseConfidence(0.0, false, weather)).isGreaterThanOrEqualTo(0.6);
        var time = List.of(
                new McpToolInvocationSummary("s", "powerrag-tools__get_current_time", true, 2L, null, null));
        assertThat(scorer.responseConfidence(0.0, false, time)).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("Failed MCP invocations do not add bonus")
    void responseConfidence_failedToolsIgnored() {
        var failed = List.of(
                new McpToolInvocationSummary("s", "powerrag-tools__fetch_url", false, 10L, "timeout", null));
        assertThat(scorer.responseConfidence(0.0, false, failed)).isEqualTo(0.0);
    }
}
