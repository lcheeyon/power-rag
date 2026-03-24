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
 * Step definitions for texttosql.feature BDD scenarios.
 */
public class TextToSqlSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private AnthropicChatModel anthropicChatModel;

    private final HealthCheckSteps healthCheckSteps;

    private Response lastResponse;

    public TextToSqlSteps(HealthCheckSteps healthCheckSteps) {
        this.healthCheckSteps = healthCheckSteps;
    }

    // ── Given ──────────────────────────────────────────────────────────────

    @Given("the SQL LLM will return {string}")
    public void theSqlLlmWillReturn(String content) {
        ChatResponse mockResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(content))));
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }

    // ── When ───────────────────────────────────────────────────────────────

    @When("I send a SQL query {string}")
    public void iSendASqlQuery(String question) {
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("question", question))
                .post("/api/sql/query");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    @When("I send a SQL query without authentication {string}")
    public void iSendASqlQueryWithoutAuthentication(String question) {
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .body(Map.of("question", question))
                .post("/api/sql/query");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    // ── Then ───────────────────────────────────────────────────────────────

    @Then("the SQL response status should be {int}")
    public void sqlResponseStatusShouldBe(int expected) {
        assertThat(lastResponse.statusCode()).isEqualTo(expected);
    }

    @And("the SQL response should contain a {string} field")
    public void sqlResponseShouldContainField(String field) {
        assertThat((Object) lastResponse.jsonPath().get(field)).isNotNull();
    }

    @And("the SQL error response should contain {string}")
    public void sqlErrorResponseShouldContain(String text) {
        String body = lastResponse.asString();
        assertThat(body).contains(text);
    }

    @And("the SQL response {string} field should not be blank")
    public void sqlResponseFieldShouldNotBeBlank(String field) {
        String value = lastResponse.jsonPath().getString(field);
        assertThat(value).isNotNull().isNotBlank();
    }
}
