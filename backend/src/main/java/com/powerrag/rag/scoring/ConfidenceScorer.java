package com.powerrag.rag.scoring;

import com.powerrag.mcp.McpToolInvocationSummary;
import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.retrieval.HybridRetriever;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Converts RRF-merged scores into a normalised [0, 1] retrieval strength, then
 * combines it with successful MCP tool usage into a single response confidence
 * for the UI.
 *
 * <p>The theoretical maximum RRF score is {@code 2/(1+k)} when a chunk
 * is ranked first in both the dense and keyword lists (RRF k=60). Real hits
 * are often well below that, so {@link #responseConfidence} applies a gentle
 * affine curve on KB-backed answers and adds bonuses when live tools returned data.
 */
@Component
public class ConfidenceScorer {

    private static final double MAX_RRF = 2.0 / (1.0 + HybridRetriever.RRF_K);

    /** Maps normalised top-chunk RRF into a user-facing band (typical hits are not near 1.0 raw). */
    private static final double KB_SCALE  = 0.78;
    private static final double KB_FLOOR  = 0.22;

    private static final double MCP_BONUS_FETCH_URL      = 0.12;
    private static final double MCP_BONUS_GITHUB         = 0.11;
    private static final double MCP_BONUS_JIRA           = 0.11;
    private static final double MCP_BONUS_WEATHER        = 0.13;
    private static final double MCP_BONUS_TIME           = 0.13;
    private static final double MCP_BONUS_GCP_LOGGING    = 0.10;
    private static final double MCP_BONUS_EMAIL          = 0.10;
    private static final double MCP_BONUS_CAP            = 0.40;

    /** When the model relied on tools only (no KB context), avoid sub-50% for successful tool data. */
    private static final double MCP_ONLY_BASE   = 0.52;
    private static final double MCP_ONLY_WEIGHT = 0.75;

    public double score(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0.0;
        double raw = chunks.get(0).score() / MAX_RRF;
        return round4(Math.min(1.0, Math.max(0.0, raw)));
    }

    /**
     * Final confidence after a successful assistant turn: calibrated KB score plus
     * stacked bonuses per successful MCP tool category (fetch, GitHub, Jira, weather, time, GCP logs, email).
     */
    public double responseConfidence(double retrievalNormalized,
                                     boolean hasRelevantDocs,
                                     List<McpToolInvocationSummary> mcpInvocations) {
        double kb = 0.0;
        if (hasRelevantDocs) {
            kb = round4(Math.min(1.0, KB_FLOOR + KB_SCALE * Math.max(0.0, Math.min(1.0, retrievalNormalized))));
        }
        double mcp = mcpToolBonus(mcpInvocations);
        double combined = kb + mcp;
        if (kb <= 0.0 && mcp > 0.0) {
            combined = Math.max(combined, MCP_ONLY_BASE + mcp * MCP_ONLY_WEIGHT);
        }
        return round4(Math.min(1.0, combined));
    }

    private static double mcpToolBonus(List<McpToolInvocationSummary> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (McpToolInvocationSummary inv : invocations) {
            if (inv == null || !inv.success()) {
                continue;
            }
            String n = inv.toolName() != null ? inv.toolName().toLowerCase(Locale.ROOT) : "";
            if (n.contains("fetch_url")) {
                sum += MCP_BONUS_FETCH_URL;
            } else if (n.contains("github_")) {
                sum += MCP_BONUS_GITHUB;
            } else if (n.contains("jira_")) {
                sum += MCP_BONUS_JIRA;
            } else if (n.contains("weather")) {
                sum += MCP_BONUS_WEATHER;
            } else if (n.contains("current_time") || n.contains("get_current_time")) {
                sum += MCP_BONUS_TIME;
            } else if (n.contains("gcp_logging")) {
                sum += MCP_BONUS_GCP_LOGGING;
            } else if (n.contains("email_")) {
                sum += MCP_BONUS_EMAIL;
            }
        }
        return Math.min(MCP_BONUS_CAP, sum);
    }

    private static double round4(double v) {
        return Math.round(Math.min(1.0, Math.max(0.0, v)) * 10_000.0) / 10_000.0;
    }
}
