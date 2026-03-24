package com.powerrag.cache;

import com.powerrag.rag.model.SourceRef;

import java.util.List;
import java.util.Optional;

/**
 * Contract for the semantic cache layer.
 * Two implementations: Redis-backed (production) and no-op (tests / disabled).
 */
public interface SemanticCache {

    /**
     * Look up a cached answer for the given query and language.
     *
     * @return the cached hit if similarity ≥ threshold and not expired; empty otherwise
     */
    Optional<CacheHit> lookup(String query, String language);

    /**
     * Store a query/answer pair in the cache for future hits.
     */
    void store(String query, String language,
               String answer, double confidence,
               List<SourceRef> sources, String modelId);
}
