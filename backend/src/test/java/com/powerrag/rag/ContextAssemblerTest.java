package com.powerrag.rag;

import com.powerrag.rag.assembly.ContextAssembler;
import com.powerrag.rag.model.RetrievedChunk;
import com.powerrag.rag.model.SourceRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContextAssembler Unit Tests")
class ContextAssemblerTest {

    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ContextAssembler();
    }

    private RetrievedChunk chunk(String text, Map<String, Object> meta) {
        return new RetrievedChunk("id", text, 0.8, meta);
    }

    @Test
    @DisplayName("assemble formats [SOURCE N] headers")
    void assemble_formatsSourceHeaders() {
        var c = chunk("Power RAG is a RAG system.",
                Map.of("file_name", "arch.pdf", "section", "page-1"));
        String ctx = assembler.assemble(List.of(c));
        assertThat(ctx).contains("[SOURCE 1]").contains("arch.pdf").contains("page-1");
    }

    @Test
    @DisplayName("assemble includes chunk text")
    void assemble_includesChunkText() {
        var c = chunk("Important content here.", Map.of("file_name", "doc.pdf", "section", ""));
        String ctx = assembler.assemble(List.of(c));
        assertThat(ctx).contains("Important content here.");
    }

    @Test
    @DisplayName("assemble numbers multiple sources sequentially")
    void assemble_numbersMultipleSources() {
        var c1 = chunk("First chunk.", Map.of("file_name", "a.pdf", "section", ""));
        var c2 = chunk("Second chunk.", Map.of("file_name", "b.pdf", "section", ""));
        String ctx = assembler.assemble(List.of(c1, c2));
        assertThat(ctx).contains("[SOURCE 1]").contains("[SOURCE 2]");
    }

    @Test
    @DisplayName("assemble empty list returns empty string")
    void assemble_emptyList_returnsEmpty() {
        assertThat(assembler.assemble(List.of())).isEmpty();
    }

    @Test
    @DisplayName("extractSources maps fileName from metadata")
    void extractSources_populatesFileName() {
        var c = chunk("text", Map.of("file_name", "Hello.java", "section", "class Hello"));
        List<SourceRef> sources = assembler.extractSources(List.of(c));
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).fileName()).isEqualTo("Hello.java");
    }

    @Test
    @DisplayName("extractSources truncates long snippet to 200 chars + ellipsis")
    void extractSources_truncatesLongText() {
        String longText = "x".repeat(300);
        var c = chunk(longText, Map.of("file_name", "f.pdf", "section", ""));
        List<SourceRef> sources = assembler.extractSources(List.of(c));
        assertThat(sources.get(0).snippet()).endsWith("…").hasSizeLessThanOrEqualTo(202);
    }

    @Test
    @DisplayName("extractSources empty list returns empty list")
    void extractSources_emptyList_returnsEmpty() {
        assertThat(assembler.extractSources(List.of())).isEmpty();
    }

    @Test
    @DisplayName("extractSources null list returns empty list")
    void extractSources_nullList_returnsEmpty() {
        assertThat(assembler.extractSources(null)).isEmpty();
    }

    @Test
    @DisplayName("extractSources preserves pageNumber from metadata")
    void extractSources_preservesPageNumber() {
        var c = chunk("text", Map.of("file_name", "report.pdf", "section", "page-3", "page_number", 3));
        List<SourceRef> sources = assembler.extractSources(List.of(c));
        assertThat(sources.get(0).pageNumber()).isEqualTo(3);
    }
}
