package com.powerrag.infrastructure;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * Shared Testcontainers configuration.
 * Imported by integration tests that need real infrastructure.
 * Uses @ServiceConnection to auto-configure Spring datasource/redis properties.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("powerrag_test")
                .withUsername("testuser")
                .withPassword("testpass")
                ;
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis/redis-stack:7.4.0-v3"))
                .withExposedPorts(6379)
                ;
    }

    /**
     * No-op VectorStore for tests where Qdrant autoconfigure is excluded.
     * HybridRetriever uses {@code @Qualifier("vectorStore")} — same qualifier as Spring AI Qdrant autoconfig.
     */
    @Bean("vectorStore")
    @ConditionalOnMissingBean(name = "vectorStore")
    public VectorStore noOpVectorStore() {
        return new VectorStore() {
            @Override public void add(List<Document> documents) {}
            @Override public void delete(List<String> idList) {}
            @Override public void delete(Filter.Expression filterExpression) {}
            @Override public List<Document> similaritySearch(SearchRequest request) { return List.of(); }
        };
    }
}
