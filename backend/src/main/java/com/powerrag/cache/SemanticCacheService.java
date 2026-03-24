package com.powerrag.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerrag.rag.model.SourceRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis Stack–backed semantic cache using vector similarity.
 * Active only when {@code powerrag.semantic-cache.enabled} is true (or absent).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "powerrag.semantic-cache.enabled", matchIfMissing = true)
public class SemanticCacheService implements SemanticCache {

    private final VectorStore    vectorStore;
    private final ObjectMapper   objectMapper;
    private final double         threshold;
    private final long           ttlMs;

    public SemanticCacheService(
            @Qualifier("semanticCacheVectorStore") VectorStore vectorStore,
            ObjectMapper objectMapper,
            @Value("${powerrag.models.cache.similarity-threshold:0.92}") double threshold,
            @Value("${powerrag.models.cache.ttl-seconds:86400}") long ttlSeconds) {
        this.vectorStore  = vectorStore;
        this.objectMapper = objectMapper;
        this.threshold    = threshold;
        this.ttlMs        = ttlSeconds * 1000L;
    }

    @Override
    public Optional<CacheHit> lookup(String query, String language) {
        try {
            String safeLang = sanitize(language);
            var b = new FilterExpressionBuilder();
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(1)
                            .similarityThreshold(threshold)
                            .filterExpression(b.eq("language", safeLang).build())
                            .build());

            if (results.isEmpty()) return Optional.empty();

            Document doc  = results.get(0);
            Map<String, Object> meta = doc.getMetadata();

            // TTL check — Redis returns numeric fields as strings; parse defensively
            Object cachedAtObj = meta.get("cached_at");
            if (cachedAtObj != null) {
                long cachedAt = (long) Double.parseDouble(String.valueOf(cachedAtObj));
                if (System.currentTimeMillis() - cachedAt > ttlMs) {
                    vectorStore.delete(List.of(doc.getId()));
                    log.debug("Semantic cache EXPIRED for lang='{}'", safeLang);
                    return Optional.empty();
                }
            }

            String         answer     = String.valueOf(meta.getOrDefault("answer", ""));
            double         confidence = Double.parseDouble(String.valueOf(meta.getOrDefault("confidence", "0.0")));
            String         modelId    = String.valueOf(meta.getOrDefault("model_id", ""));
            List<SourceRef> sources   = deserializeSources((String) meta.getOrDefault("sources", "[]"));

            log.debug("Semantic cache HIT for lang='{}'", safeLang);
            return Optional.of(new CacheHit(answer, confidence, sources, modelId));

        } catch (Exception e) {
            log.warn("Semantic cache lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void store(String query, String language,
                      String answer, double confidence,
                      List<SourceRef> sources, String modelId) {
        try {
            String safeLang = sanitize(language);
            Map<String, Object> meta = new HashMap<>();
            meta.put("answer",     answer);
            meta.put("language",   safeLang);
            meta.put("confidence", confidence);
            meta.put("model_id",   modelId);
            meta.put("sources",    serializeSources(sources));
            meta.put("cached_at",  (double) System.currentTimeMillis());

            vectorStore.add(List.of(new Document(query, meta)));
            log.debug("Semantic cache STORE for lang='{}'", safeLang);
        } catch (Exception e) {
            log.warn("Semantic cache store failed: {}", e.getMessage());
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Normalises language values for use as Redis FTS TAG field values.
     * Hyphens are replaced with underscores because Redis FTS treats '-' as
     * a negation operator inside TAG queries (e.g. {@code @language:{test-abc}}
     * would be mis-parsed). All other non-alphanumeric/underscore chars are removed.
     */
    private String sanitize(String value) {
        if (value == null) return "en";
        return value.replace('-', '_').replaceAll("[^a-zA-Z0-9_]", "");
    }

    private String serializeSources(List<SourceRef> sources) {
        try {
            return objectMapper.writeValueAsString(sources != null ? sources : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<SourceRef> deserializeSources(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<SourceRef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
