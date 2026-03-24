package com.powerrag.ingestion.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * A text chunk ready for embedding and storage in Qdrant.
 * Carries both content and metadata that will be indexed alongside the vector.
 */
@Getter
@Builder
public class Chunk {

    /** Chunk text content. */
    private final String text;

    /** Zero-based position within the document. */
    private final int index;

    /**
     * Metadata stored alongside the embedding in Qdrant.
     * Common keys: file_name, doc_type, page_number, sheet_name,
     *              row_number, class_name, method_name, section, heading.
     */
    private final Map<String, Object> metadata;
}
