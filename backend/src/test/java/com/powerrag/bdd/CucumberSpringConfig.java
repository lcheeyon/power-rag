package com.powerrag.bdd;

import com.powerrag.infrastructure.TestContainersConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Wires the Spring context into the Cucumber test lifecycle.
 * Testcontainers provide real PostgreSQL and Redis for BDD scenarios.
 * LLM models are mocked to prevent real API calls across all scenarios.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
public class CucumberSpringConfig {

    @LocalServerPort
    int port;

    /** Replaced with a Mockito mock so no real Anthropic API call is made. */
    @MockitoBean
    AnthropicChatModel anthropicChatModel;

    /** Replaced with a Mockito mock so no real Ollama call is made (used by guardrail service). */
    @MockitoBean
    OllamaChatModel ollamaChatModel;
}
