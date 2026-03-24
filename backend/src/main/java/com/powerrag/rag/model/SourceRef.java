package com.powerrag.rag.model;

/**
 * A user-visible citation pointing to the source of a retrieved chunk.
 */
public record SourceRef(
        String fileName,
        String section,
        String snippet,
        Object pageNumber,
        Integer lineNumber,
        String documentId
) {}
