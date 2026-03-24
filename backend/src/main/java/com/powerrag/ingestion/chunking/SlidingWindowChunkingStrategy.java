package com.powerrag.ingestion.chunking;

import com.powerrag.ingestion.model.Chunk;
import com.powerrag.ingestion.model.ParsedSection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sliding-window chunking: splits each section's text into overlapping word-based windows.
 *
 * <p>Words (whitespace-split tokens) are used as the unit rather than sub-word tokens,
 * which is a reasonable approximation for nomic-embed-text's 512-token context window.
 */
@Component
public class SlidingWindowChunkingStrategy implements ChunkingStrategy {

    private final int chunkSize;
    private final int chunkOverlap;

    public SlidingWindowChunkingStrategy(
            @Value("${powerrag.ingestion.chunk-size:512}") int chunkSize,
            @Value("${powerrag.ingestion.chunk-overlap:64}") int chunkOverlap) {
        this.chunkSize    = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public int getChunkSize()    { return chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }

    @Override
    public List<Chunk> chunk(List<ParsedSection> sections) {
        List<Chunk> chunks = new ArrayList<>();
        int globalIndex = 0;

        for (ParsedSection section : sections) {
            String sectionText = section.getText();
            String[] words = sectionText.split("\\s+");
            int step = Math.max(1, chunkSize - chunkOverlap);
            int start = 0;

            // Build word→line mapping for start_line calculation
            int[] wordLine = buildWordLineMap(sectionText);

            while (start < words.length) {
                int end = Math.min(start + chunkSize, words.length);
                String text = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));

                Map<String, Object> meta = new HashMap<>(section.getMetadata());
                meta.put("chunk_index", globalIndex);
                // Only set start_line if the section metadata doesn't already have line_number
                if (!meta.containsKey("line_number") && wordLine.length > start) {
                    meta.put("start_line", wordLine[start]);
                }

                chunks.add(Chunk.builder()
                        .text(text)
                        .index(globalIndex++)
                        .metadata(meta)
                        .build());

                if (end == words.length) break;
                start += step;
            }
        }
        return chunks;
    }

    /** Maps each word index (in split-by-whitespace order) to its 1-based line number. */
    private int[] buildWordLineMap(String text) {
        String[] lines = text.split("\n", -1);
        List<Integer> lineNumbers = new ArrayList<>();
        for (int lineNum = 1; lineNum <= lines.length; lineNum++) {
            String[] lineWords = lines[lineNum - 1].trim().split("\\s+");
            for (String w : lineWords) {
                if (!w.isBlank()) lineNumbers.add(lineNum);
            }
        }
        return lineNumbers.stream().mapToInt(Integer::intValue).toArray();
    }
}
