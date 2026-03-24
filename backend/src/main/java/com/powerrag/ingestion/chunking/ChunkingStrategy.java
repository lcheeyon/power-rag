package com.powerrag.ingestion.chunking;

import com.powerrag.ingestion.model.Chunk;
import com.powerrag.ingestion.model.ParsedSection;

import java.util.List;

/**
 * Strategy for splitting parsed sections into fixed-size overlapping chunks.
 */
public interface ChunkingStrategy {

    /**
     * Split the given sections into chunks ready for embedding.
     *
     * @param sections ordered list of parsed sections from a document parser
     * @return ordered list of chunks; chunk indices are sequential across all sections
     */
    List<Chunk> chunk(List<ParsedSection> sections);
}
