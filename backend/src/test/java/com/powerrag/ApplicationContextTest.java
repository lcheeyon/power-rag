package com.powerrag;

import com.powerrag.infrastructure.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring application context loads successfully
 * with all beans wired (DB, Redis, Security, AI configs).
 *
 * LLM provider calls are not made – only context wiring is tested.
 * Real PostgreSQL and Redis are provided by Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("Application Context Load Test")
class ApplicationContextTest {

    @Test
    @DisplayName("Spring context loads without errors")
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("Core beans are present in context")
    void coreBeansPresentInContext(ApplicationContext context) {
        assertThat(context.containsBean("securityConfig")).isTrue();
        assertThat(context.containsBean("jwtService")).isTrue();
        assertThat(context.containsBean("jwtAuthFilter")).isTrue();
        assertThat(context.containsBean("userDetailsServiceImpl")).isTrue();
        assertThat(context.containsBean("healthController")).isTrue();
        assertThat(context.containsBean("authController")).isTrue();
    }
}
