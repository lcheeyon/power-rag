package com.powerrag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * Configures the Redis Stack vector store used for semantic caching.
 * Qdrant is configured via application.yml auto-configuration for the knowledge base.
 */
@Configuration
public class VectorStoreConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * JedisPooled uses RedisConnectionDetails (provided by @ServiceConnection in tests)
     * when available, otherwise falls back to @Value-resolved host/port.
     * This ensures test containers are used instead of a local Redis instance.
     */
    @Bean
    @ConditionalOnProperty(name = "powerrag.semantic-cache.enabled", matchIfMissing = true)
    public JedisPooled jedisPooled(
            @Autowired(required = false) RedisConnectionDetails redisConnectionDetails) {
        if (redisConnectionDetails != null
                && redisConnectionDetails.getStandalone() != null) {
            var cfg = redisConnectionDetails.getStandalone();
            return new JedisPooled(cfg.getHost(), cfg.getPort());
        }
        return new JedisPooled(redisHost, redisPort);
    }

    /**
     * Semantic cache vector store backed by Redis Stack.
     * Uses HNSW index for sub-millisecond approximate nearest-neighbour search.
     * Disabled in test profile via powerrag.semantic-cache.enabled=false.
     */
    @Bean("semanticCacheVectorStore")
    @ConditionalOnProperty(name = "powerrag.semantic-cache.enabled", matchIfMissing = true)
    public VectorStore semanticCacheVectorStore(JedisPooled jedisPooled,
                                                EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("power-rag-semantic-cache")
                .prefix("semantic-cache:")
                .metadataFields(
                        RedisVectorStore.MetadataField.text("answer"),
                        RedisVectorStore.MetadataField.tag("language"),
                        RedisVectorStore.MetadataField.text("sources"),
                        RedisVectorStore.MetadataField.numeric("confidence"),
                        RedisVectorStore.MetadataField.numeric("cached_at"),
                        RedisVectorStore.MetadataField.text("model_id")
                )
                .initializeSchema(true)
                .build();
    }
}
