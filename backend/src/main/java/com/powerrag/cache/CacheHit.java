package com.powerrag.cache;

import com.powerrag.rag.model.SourceRef;

import java.util.List;

/**
 * Immutable value returned when a semantic cache hit is found.
 */
public record CacheHit(
        String answer,
        double confidence,
        List<SourceRef> sources,
        String modelId
) {}
