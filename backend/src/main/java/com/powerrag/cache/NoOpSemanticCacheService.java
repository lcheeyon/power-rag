package com.powerrag.cache;

import com.powerrag.rag.model.SourceRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * No-op semantic cache used when {@code powerrag.semantic-cache.enabled=false}.
 * Every lookup is a miss; every store is a silent no-op.
 */
@Service
@ConditionalOnProperty(name = "powerrag.semantic-cache.enabled", havingValue = "false")
public class NoOpSemanticCacheService implements SemanticCache {

    @Override
    public Optional<CacheHit> lookup(String query, String language) {
        return Optional.empty();
    }

    @Override
    public void store(String query, String language,
                      String answer, double confidence,
                      List<SourceRef> sources, String modelId) {
        // no-op
    }
}
