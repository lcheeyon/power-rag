package com.powerrag.mcp;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects MCP tool invocations for the current LLM call (one thread per HTTP request).
 */
@Component
public class McpInvocationRecorder {

    private final ThreadLocal<List<McpToolInvocationSummary>> current = ThreadLocal.withInitial(ArrayList::new);

    public void clear() {
        current.get().clear();
    }

    public void record(McpToolInvocationSummary summary) {
        current.get().add(summary);
    }

    /** Returns an immutable snapshot and clears the buffer for this thread. */
    public List<McpToolInvocationSummary> snapshotAndClear() {
        List<McpToolInvocationSummary> list = new ArrayList<>(current.get());
        current.get().clear();
        return list.isEmpty() ? List.of() : Collections.unmodifiableList(list);
    }
}
