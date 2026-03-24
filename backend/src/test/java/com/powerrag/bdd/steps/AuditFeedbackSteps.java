package com.powerrag.bdd.steps;

import com.powerrag.domain.FeedbackRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for audit_feedback.feature BDD scenarios.
 */
public class AuditFeedbackSteps {

    @LocalServerPort
    private int port;

    @Autowired private HealthCheckSteps   healthCheckSteps;
    @Autowired private FeedbackRepository feedbackRepository;

    private Response lastFeedbackResponse;
    private UUID     lastInteractionId;

    @Before
    public void clearFeedback() {
        feedbackRepository.deleteAll();
    }

    // ── When ───────────────────────────────────────────────────────────────

    @When("I request the admin interactions endpoint")
    public void iRequestAdminInteractionsEndpoint() {
        Response response = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .get("/api/admin/interactions");
        healthCheckSteps.setLastResponse(response);
    }

    @When("I submit a {int}-star rating for the last interaction")
    public void iSubmitStarRatingForLastInteraction(int stars) {
        lastInteractionId = UUID.fromString(
                healthCheckSteps.getLastResponse().jsonPath().getString("interactionId"));
        lastFeedbackResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("rating", stars))
                .post("/api/interactions/" + lastInteractionId + "/feedback");
    }

    @When("I submit a {int}-star rating for the last interaction again")
    public void iSubmitStarRatingForLastInteractionAgain(int stars) {
        lastFeedbackResponse = RestAssured.given()
                .baseUri("http://localhost").port(port)
                .contentType("application/json")
                .header("Authorization", "Bearer " + healthCheckSteps.getJwtToken())
                .body(Map.of("rating", stars))
                .post("/api/interactions/" + lastInteractionId + "/feedback");
    }

    // ── Then ───────────────────────────────────────────────────────────────

    @Then("the admin interactions response status should be {int}")
    public void adminInteractionsResponseStatusShouldBe(int expectedStatus) {
        assertThat(healthCheckSteps.getLastResponse().statusCode()).isEqualTo(expectedStatus);
    }

    @And("the admin interactions response should contain interactions")
    public void adminInteractionsResponseShouldContainInteractions() {
        assertThat(healthCheckSteps.getLastResponse().jsonPath().<Object>get("content")).isNotNull();
        assertThat(healthCheckSteps.getLastResponse().jsonPath().<java.util.List<?>>get("content"))
                .isNotEmpty();
    }

    @Then("the feedback response status should be {int}")
    public void feedbackResponseStatusShouldBe(int expectedStatus) {
        assertThat(lastFeedbackResponse.statusCode()).isEqualTo(expectedStatus);
    }
}
