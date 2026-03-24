package com.powerrag.ingestion.chunking;

import com.powerrag.ingestion.model.Chunk;
import com.powerrag.ingestion.model.ParsedSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlidingWindowChunkingStrategy Unit Tests")
class ChunkingStrategyTest {

    private SlidingWindowChunkingStrategy strategy(int size, int overlap) {
        return new SlidingWindowChunkingStrategy(size, overlap);
    }

    @Test
    @DisplayName("Short text fits in one chunk")
    void shortTextProducesOneChunk() {
        var s = strategy(512, 64);
        List<ParsedSection> sections = List.of(section("hello world", Map.of("doc_type", "PDF")));
        List<Chunk> chunks = s.chunk(sections);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).contains("hello world");
    }

    @Test
    @DisplayName("Text longer than chunkSize produces multiple chunks")
    void longTextProducesMultipleChunks() {
        var s = strategy(10, 2); // 10 words per chunk, 2 overlap
        String text = "w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11 w12 w13 w14 w15 w16 w17 w18 w19 w20";
        List<ParsedSection> sections = List.of(section(text, Map.of()));
        List<Chunk> chunks = s.chunk(sections);
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Chunk size is correctly limited")
    void chunkSizeIsRespected() {
        var s = strategy(5, 0);
        // 15 words → 3 chunks of 5
        String text = "a b c d e f g h i j k l m n o";
        List<Chunk> chunks = s.chunk(List.of(section(text, Map.of())));
        assertThat(chunks).hasSize(3);
        for (Chunk c : chunks) {
            assertThat(c.getText().split("\\s+").length).isLessThanOrEqualTo(5);
        }
    }

    @Test
    @DisplayName("Overlap ensures last N words of previous chunk appear in next chunk")
    void overlapWordsCarryOver() {
        var s = strategy(6, 2);
        // 10 words: chunk1=[w1..w6], chunk2=[w5,w6,w7..w10]
        String text = "w1 w2 w3 w4 w5 w6 w7 w8 w9 w10";
        List<Chunk> chunks = s.chunk(List.of(section(text, Map.of())));
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).getText()).startsWith("w5");
    }

    @Test
    @DisplayName("Chunk indices are sequential across sections")
    void chunkIndicesAreSequential() {
        var s = strategy(5, 0);
        List<ParsedSection> sections = List.of(
                section("a b c d e", Map.of()),
                section("f g h i j", Map.of())
        );
        List<Chunk> chunks = s.chunk(sections);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getIndex()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("Section metadata is forwarded to every chunk")
    void metadataIsForwardedToChunks() {
        var s = strategy(3, 0);
        Map<String, Object> meta = Map.of("file_name", "test.pdf", "page_number", 1);
        List<Chunk> chunks = s.chunk(List.of(section("a b c d e f", meta)));
        for (Chunk c : chunks) {
            assertThat(c.getMetadata()).containsEntry("file_name", "test.pdf");
            assertThat(c.getMetadata()).containsEntry("page_number", 1);
            assertThat(c.getMetadata()).containsKey("chunk_index");
        }
    }

    @Test
    @DisplayName("Empty sections list returns empty chunks")
    void emptyInputReturnsEmpty() {
        var s = strategy(512, 64);
        assertThat(s.chunk(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getChunkSize and getChunkOverlap return configured values")
    void gettersReturnConfiguredValues() {
        var s = strategy(256, 32);
        assertThat(s.getChunkSize()).isEqualTo(256);
        assertThat(s.getChunkOverlap()).isEqualTo(32);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ParsedSection section(String text, Map<String, Object> meta) {
        return ParsedSection.builder().text(text).metadata(meta).build();
    }
}
