package com.powerrag.ingestion.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * A logical section extracted from a document by a parser.
 * Each section becomes the unit of text that feeds the ChunkingStrategy.
 */
@Getter
@Builder
public class ParsedSection {

    /** Raw text content of this section. */
    private final String text;

    /**
     * Metadata specific to this section (e.g. page_number, class_name, sheet_name).
     * Will be merged with document-level metadata and forwarded to each Chunk.
     */
    private final Map<String, Object> metadata;
}
