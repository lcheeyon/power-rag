package com.powerrag.api;

import java.util.List;

/**
 * MCP tools exposed to the RAG chat client (for UI and discovery).
 */
public record McpToolsResponse(
        boolean ragMcpEnabled,
        boolean mcpClientAvailable,
        List<McpToolEntry> tools
) {
    public record McpToolEntry(String name, String description) {}
}
