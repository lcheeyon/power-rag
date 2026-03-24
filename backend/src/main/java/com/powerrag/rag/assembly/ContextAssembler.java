package com.powerrag.rag.assembly;

import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.model.SourceRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Formats retrieved chunks into an LLM context string and extracts source citations.
 */
@Component
public class ContextAssembler {

    private static final int SNIPPET_MAX = 200;

    @Value("${powerrag.rag.max-context-chars:24000}")
    private int maxContextChars = 24000;

    /**
     * Builds the context block that is prepended to the user prompt.
     */
    public String assemble(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return "";

        var sb = new StringBuilder();
        int totalChars = 0;

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            String ref = buildRef(c);
            String entry = String.format("[SOURCE %d] %s%n%s%n%n", i + 1, ref, c.text());

            if (totalChars + entry.length() > maxContextChars) break;
            sb.append(entry);
            totalChars += entry.length();
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Maps each chunk to a user-visible citation record.
     */
    public List<SourceRef> extractSources(List<RetrievedChunk> chunks) {
        if (chunks == null) return List.of();
        return chunks.stream()
                .map(c -> new SourceRef(
                        str(c.metadata().get("file_name")),
                        buildRef(c),
                        snippet(c.text()),
                        c.metadata().get("page_number"),
                        toInt(c.metadata().get("line_number") != null
                                ? c.metadata().get("line_number")
                                : c.metadata().get("row_number") != null
                                        ? c.metadata().get("row_number")
                                        : c.metadata().get("start_line")),
                        str(c.metadata().get("document_id"))))
                .toList();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private String buildRef(RetrievedChunk c) {
        String file    = str(c.metadata().get("file_name"));
        String section = str(c.metadata().get("section"));
        return section.isEmpty() ? file : file + " § " + section;
    }

    private String snippet(String text) {
        if (text == null) return "";
        return text.length() > SNIPPET_MAX ? text.substring(0, SNIPPET_MAX) + "…" : text;
    }

    private String str(Object val) {
        return val != null ? val.toString() : "";
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
