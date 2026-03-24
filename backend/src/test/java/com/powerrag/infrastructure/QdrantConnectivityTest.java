package com.powerrag.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Qdrant vector DB connectivity using Testcontainers.
 * Checks the REST health endpoint and collection management API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Testcontainers
@DisplayName("Qdrant Vector DB Connectivity Tests")
class QdrantConnectivityTest {

    @Container
    static GenericContainer<?> qdrantContainer =
            new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.13.4"))
                    .withExposedPorts(6333, 6334)
                    .withReuse(true);

    @DynamicPropertySource
    static void qdrantProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.qdrant.host",
                qdrantContainer::getHost);
        registry.add("spring.ai.vectorstore.qdrant.port",
                () -> qdrantContainer.getMappedPort(6334));
    }

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    @DisplayName("Qdrant REST health endpoint returns OK")
    void qdrantHealthEndpointReturnsOk() {
        String baseUrl = "http://" + qdrantContainer.getHost()
                + ":" + qdrantContainer.getMappedPort(6333);
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/healthz", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("Qdrant collections endpoint is reachable")
    void qdrantCollectionsEndpointIsReachable() {
        String baseUrl = "http://" + qdrantContainer.getHost()
                + ":" + qdrantContainer.getMappedPort(6333);
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/collections", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKey("result");
    }

    @Test
    @DisplayName("Qdrant version endpoint returns version info")
    void qdrantVersionEndpointReturnsVersionInfo() {
        String baseUrl = "http://" + qdrantContainer.getHost()
                + ":" + qdrantContainer.getMappedPort(6333);
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("title")).isEqualTo("qdrant - vector search engine");
    }
}
