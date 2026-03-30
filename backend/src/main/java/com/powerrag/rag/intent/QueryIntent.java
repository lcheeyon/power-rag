package com.powerrag.rag.intent;

/**
 * Routing decision before retrieval and main LLM call.
 *
 * @param retrieveDocuments whether to run hybrid retrieval against the knowledge base
 * @param allowMcpTools     whether MCP tool callbacks (e.g. fetch) may be attached to the main call
 */
public record QueryIntent(boolean retrieveDocuments, boolean allowMcpTools) {

    /** When intent routing is disabled: always retrieve; MCP follows global {@code powerrag.mcp.rag-enabled}. */
    public static QueryIntent legacy(boolean mcpRagEnabled) {
        return new QueryIntent(true, mcpRagEnabled);
    }
}
