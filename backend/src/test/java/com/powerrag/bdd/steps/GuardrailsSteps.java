package com.powerrag.bdd.steps;

import com.powerrag.guardrails.GuardrailFlag;
import com.powerrag.guardrails.GuardrailFlagRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.restassured.response.Response;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Step definitions for guardrails.feature BDD scenarios.
 */
public class GuardrailsSteps {

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Autowired
    private AnthropicChatModel anthropicChatModel;

    @Autowired
    private GuardrailFlagRepository guardrailFlagRepository;

    @Autowired
    private HealthCheckSteps healthCheckSteps;

    @Before
    public void clearGuardrailFlags() {
        guardrailFlagRepository.deleteAll();
    }

    // ── Given ──────────────────────────────────────────────────────────────

    @Given("the guardrail model classifies input as safe")
    public void theGuardrailModelClassifiesInputAsSafe() {
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("safe")))));
    }

    @Given("the guardrail model classifies input as unsafe with category {string}")
    public void theGuardrailModelClassifiesInputAsUnsafe(String category) {
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("unsafe\n" + category)))));
    }

    @Given("the LLM will respond with {string}")
    public void theLlmWillRespondWith(String answer) {
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(answer)))));
    }

    // ── Then ───────────────────────────────────────────────────────────────

    @And("the guardrail response answer should contain {string}")
    public void guardrailResponseAnswerShouldContain(String expected) {
        Response response = healthCheckSteps.getLastResponse();
        String answer = response.jsonPath().getString("answer");
        assertThat(answer).contains(expected);
    }

    @And("the guardrail response answer should not contain {string}")
    public void guardrailResponseAnswerShouldNotContain(String unexpected) {
        Response response = healthCheckSteps.getLastResponse();
        String answer = response.jsonPath().getString("answer");
        assertThat(answer).doesNotContain(unexpected);
    }

    @And("a guardrail flag with stage {string} should exist in the database")
    public void guardrailFlagWithStageShouldExist(String stage) {
        List<GuardrailFlag> flags = guardrailFlagRepository.findAll();
        boolean found = flags.stream().anyMatch(f -> stage.equals(f.getStage()));
        assertThat(found)
                .as("Expected a guardrail flag with stage '%s' in the database", stage)
                .isTrue();
    }
}
