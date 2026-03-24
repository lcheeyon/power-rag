package com.powerrag.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Redis Stack connectivity: basic key-value ops and TTL.
 * Uses Testcontainers redis/redis-stack image.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("Redis Stack Connectivity Tests")
class RedisConnectivityTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Redis connection is established via PING")
    void redisConnectionIsEstablished() {
        String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
        assertThat(pong).isEqualToIgnoringCase("PONG");
    }

    @Test
    @DisplayName("Basic set and get operations work")
    void basicSetAndGetOperationsWork() {
        redisTemplate.opsForValue().set("test:phase1:key", "phase1-value");
        String value = redisTemplate.opsForValue().get("test:phase1:key");
        assertThat(value).isEqualTo("phase1-value");
        redisTemplate.delete("test:phase1:key");
    }

    @Test
    @DisplayName("Key expiry (TTL) is honoured")
    void keyExpiryIsTtlHonoured() throws InterruptedException {
        redisTemplate.opsForValue().set("test:ttl:key", "expires-soon",
                Duration.ofSeconds(1));
        assertThat(redisTemplate.hasKey("test:ttl:key")).isTrue();
        Thread.sleep(1200);
        assertThat(redisTemplate.hasKey("test:ttl:key")).isFalse();
    }

    @Test
    @DisplayName("Key deletion works correctly")
    void keyDeletionWorksCorrectly() {
        redisTemplate.opsForValue().set("test:delete:key", "to-delete");
        assertThat(redisTemplate.hasKey("test:delete:key")).isTrue();
        redisTemplate.delete("test:delete:key");
        assertThat(redisTemplate.hasKey("test:delete:key")).isFalse();
    }
}
