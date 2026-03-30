package com.powerrag.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Wraps an MCP {@link ToolCallback} to record timing and outcome into {@link McpInvocationRecorder}.
 */
public final class ObservingToolCallback implements ToolCallback {

    private static final int ARGS_SUMMARY_MAX = 200;

    private final ToolCallback delegate;
    private final McpInvocationRecorder recorder;

    public ObservingToolCallback(ToolCallback delegate, McpInvocationRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return invoke(() -> delegate.call(toolInput), toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return invoke(() -> delegate.call(toolInput, toolContext), toolInput);
    }

    private String invoke(java.util.function.Supplier<String> supplier, String toolInput) {
        String toolName = delegate.getToolDefinition().name();
        String serverId = inferServerId(toolName);
        long t0 = System.currentTimeMillis();
        try {
            String out = normalizeMcpToolOutput(supplier.get());
            long ms = System.currentTimeMillis() - t0;
            recorder.record(new McpToolInvocationSummary(serverId, toolName, true, ms, null, summarizeArgs(toolInput)));
            return out;
        } catch (RuntimeException e) {
            long ms = System.currentTimeMillis() - t0;
            String msg = e.getMessage();
            if (msg != null && msg.length() > 200) {
                msg = msg.substring(0, 200) + "…";
            }
            recorder.record(new McpToolInvocationSummary(serverId, toolName, false, ms, msg, summarizeArgs(toolInput)));
            throw e;
        }
    }

    static String inferServerId(String toolName) {
        if (toolName == null) return "mcp";
        int sep = toolName.indexOf("__");
        if (sep > 0) return toolName.substring(0, sep);
        return "mcp";
    }

    static String summarizeArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) return null;
        String s = toolInput.replaceAll("\\s+", " ").strip();
        if (s.length() > ARGS_SUMMARY_MAX) return s.substring(0, ARGS_SUMMARY_MAX) + "…";
        return s;
    }

    /**
     * Some MCP Java bridges pass through {@code TextContent#toString()} (e.g.
     * {@code TextContent[annotations=null, text=..., meta=null]}) instead of the raw text. That breaks
     * Gemini / JSON tool pipelines with "Failed to parse JSON". Extract the {@code text=} payload.
     */
    static String normalizeMcpToolOutput(String out) {
        if (out == null || out.isEmpty()) {
            return out;
        }
        String t = out.strip();
        if (!t.startsWith("TextContent[")) {
            return out;
        }
        int te = t.indexOf("text=");
        if (te < 0) {
            return out;
        }
        int valueStart = te + "text=".length();
        int meta = t.lastIndexOf(", meta=");
        if (meta < valueStart) {
            return out;
        }
        return t.substring(valueStart, meta);
    }

    public static ToolCallback[] wrapAll(ToolCallback[] originals, McpInvocationRecorder recorder) {
        if (originals == null || originals.length == 0) return originals;
        ToolCallback[] out = new ToolCallback[originals.length];
        for (int i = 0; i < originals.length; i++) {
            out[i] = new ObservingToolCallback(originals[i], recorder);
        }
        return out;
    }
}
