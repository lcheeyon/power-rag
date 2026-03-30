package com.powerrag.rag.model;

import com.powerrag.mcp.McpToolInvocationSummary;

import java.util.List;
import java.util.UUID;

/**
 * The full response from the RAG pipeline returned to the caller.
 */
public record RagResponse(
        String answer,
        double confidence,
        List<SourceRef> sources,
        String modelId,
        long durationMs,
        UUID interactionId,
        boolean cacheHit,
        String error,
        String generatedImageBase64,   // non-null when an image was generated; data-URL e.g. "data:image/png;base64,..."
        List<McpToolInvocationSummary> mcpInvocations
) {
    public RagResponse {
        if (mcpInvocations == null) {
            mcpInvocations = List.of();
        }
    }

    /** True when the LLM invoked at least one MCP tool during this response. */
    public boolean mcpToolsUsed() {
        return !mcpInvocations.isEmpty();
    }
}
