package com.powerrag.bdd.steps;

import com.powerrag.ingestion.service.DocumentIngestionService;
import io.cucumber.java.Before;
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
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Step definitions for rag.feature BDD scenarios.
 */
public class RagSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private AnthropicChatModel anthropicChatModel;

    @Autowired
    private DocumentIngestionService ingestionService;

    private Response lastResponse;

    private final HealthCheckSteps healthCheckSteps;

    public RagSteps(HealthCheckSteps healthCheckSteps) {
        this.healthCheckSteps = healthCheckSteps;
    }

    /** Configure the LLM mock before every RAG scenario. */
    @Before
    public void configureLlmMock() {
        ChatResponse mockResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        "Power RAG is a retrieval-augmented generation system. [SOURCE 1]"))));
        when(anthropicChatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }

    // ── Given ──────────────────────────────────────────────────────────────

    @Given("a document has been ingested for RAG")
    public void aDocumentHasBeenIngested() {
        String javaSource = """
                package com.example;
                public class PowerRag {
                    public String describe() { return "Power RAG is a RAG system."; }
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "PowerRag.java", "text/plain",
                javaSource.getBytes(StandardCharsets.UTF_8));
        ingestionService.ingest(file, "BDD test document", null);
    }

    // ── When ───────────────────────────────────────────────────────────────

    @When("I send a RAG query {string}")
    public void iSendARagQuery(String question) {
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("question", question))
                .post("/api/chat/query");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    @When("I send a RAG query without authentication {string}")
    public void iSendARagQueryWithoutAuth(String question) {
        lastResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .body(Map.of("question", question))
                .post("/api/chat/query");
        healthCheckSteps.setLastResponse(lastResponse);
    }

    // ── Then ───────────────────────────────────────────────────────────────

    @Then("the RAG response status should be {int}")
    public void ragResponseStatusShouldBe(int expected) {
        assertThat(lastResponse.statusCode()).isEqualTo(expected);
    }

    @And("the RAG response should contain an answer")
    public void ragResponseShouldContainAnswer() {
        String answer = lastResponse.jsonPath().getString("answer");
        assertThat(answer).isNotBlank();
    }

    @And("the RAG response should contain a confidence score")
    public void ragResponseShouldContainConfidence() {
        assertThat((Object) lastResponse.jsonPath().get("confidence")).isNotNull();
    }

    @And("the RAG response should contain a {string} field")
    public void ragResponseShouldContainField(String field) {
        assertThat((Object) lastResponse.jsonPath().get(field)).isNotNull();
    }
}
