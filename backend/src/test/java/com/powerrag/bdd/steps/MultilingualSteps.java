package com.powerrag.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Step definitions for multilingual.feature BDD scenarios.
 */
public class MultilingualSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private AnthropicChatModel anthropicChatModel;

    private final HealthCheckSteps healthCheckSteps;

    private Response lastPreferenceResponse;

    public MultilingualSteps(HealthCheckSteps healthCheckSteps) {
        this.healthCheckSteps = healthCheckSteps;
    }

    // ── Given ──────────────────────────────────────────────────────────────

    @Given("I have set my language preference to {string}")
    public void iHaveSetMyLanguagePreferenceTo(String language) {
        Response resp = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("preferredLanguage", language))
                .put("/api/user/preferences");
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Given("the LLM will respond in Chinese with {string}")
    public void theLlmWillRespondInChineseWith(String content) {
        ChatResponse mockResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(content))));
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }

    // ── When ───────────────────────────────────────────────────────────────

    @When("I update my language preference to {string}")
    public void iUpdateMyLanguagePreferenceTo(String language) {
        lastPreferenceResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("preferredLanguage", language))
                .put("/api/user/preferences");
        healthCheckSteps.setLastResponse(lastPreferenceResponse);
    }

    @When("I send a RAG query with language {string} {string}")
    public void iSendARagQueryWithLanguage(String language, String question) {
        Response resp = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("question", question, "language", language))
                .post("/api/chat/query");
        healthCheckSteps.setLastResponse(resp);
    }

    @When("I request preferences without authentication")
    public void iRequestPreferencesWithoutAuthentication() {
        lastPreferenceResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .get("/api/user/preferences");
        healthCheckSteps.setLastResponse(lastPreferenceResponse);
    }

    // ── Then ───────────────────────────────────────────────────────────────

    @Then("the preference response status should be {int}")
    public void preferenceResponseStatusShouldBe(int expected) {
        assertThat(lastPreferenceResponse.statusCode()).isEqualTo(expected);
    }

    @And("the preference response should contain language {string}")
    public void preferenceResponseShouldContainLanguage(String language) {
        String actual = lastPreferenceResponse.jsonPath().getString("preferredLanguage");
        assertThat(actual).isEqualTo(language);
    }

}
