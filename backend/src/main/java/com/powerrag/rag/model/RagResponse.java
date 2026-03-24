package com.powerrag.rag.model;

import java.util.List;
import java.util.UUID;

/**
 * The full response from the RAG pipeline returned to the caller.
 */
public record RagResponse(
        String answer,
        double confidence,
        List<SourceRef> sources,
        String modelId,
        long durationMs,
        UUID interactionId,
        boolean cacheHit,
        String error,
        String generatedImageBase64   // non-null when an image was generated; data-URL e.g. "data:image/png;base64,..."
) {}
