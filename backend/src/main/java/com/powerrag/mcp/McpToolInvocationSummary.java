package com.powerrag.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One MCP tool invocation surfaced to the API and stored on {@code interactions}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolInvocationSummary(
        String serverId,
        String toolName,
        boolean success,
        long durationMs,
        String errorMessage,
        String argsSummary
) {}
