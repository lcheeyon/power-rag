package com.powerrag.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for health.feature BDD scenarios.
 */
public class HealthCheckSteps {

    @LocalServerPort
    private int port;

    private Response lastResponse;
    private String   jwtToken;

    private RequestSpecification baseRequest() {
        return RestAssured
                .given()
                .baseUri("http://localhost")
                .port(port)
                .contentType("application/json");
    }

    // ── Given ──────────────────────────────────────────────────────────────

    @Given("the Power RAG application is running")
    public void thePowerRagApplicationIsRunning() {
        Response health = baseRequest().get("/api/public/health");
        assertThat(health.statusCode()).isEqualTo(200);
    }

    @Given("a user with username {string} and password {string} exists")
    public void aUserExists(String username, String password) {
        // Admin user is seeded by Flyway V1 migration – nothing to do here
        assertThat(username).isNotBlank();
    }

    @Given("I have a valid JWT token for user {string}")
    public void iHaveAValidJwtTokenForUser(String username) {
        lastResponse = baseRequest()
                .body(Map.of("username", username, "password", "Admin@1234"))
                .post("/api/auth/login");
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        jwtToken = lastResponse.jsonPath().getString("token");
        assertThat(jwtToken).isNotBlank();
    }

    // ── When ───────────────────────────────────────────────────────────────

    @When("I request the application health endpoint")
    public void iRequestApplicationHealthEndpoint() {
        lastResponse = baseRequest().get("/api/public/health");
    }

    @When("I request the application version endpoint")
    public void iRequestApplicationVersionEndpoint() {
        lastResponse = baseRequest().get("/api/public/version");
    }

    @When("I request the actuator health endpoint")
    public void iRequestActuatorHealthEndpoint() {
        lastResponse = baseRequest().get("/actuator/health");
    }

    @When("I request the protected chat endpoint without authentication")
    public void iRequestProtectedChatWithoutAuth() {
        lastResponse = baseRequest().get("/api/chat/query");
    }

    @When("I request the admin interactions endpoint without authentication")
    public void iRequestAdminInteractionsWithoutAuth() {
        lastResponse = baseRequest().get("/api/admin/interactions");
    }

    @When("I send a login request with username {string} and password {string}")
    public void iSendALoginRequest(String username, String password) {
        lastResponse = baseRequest()
                .body(Map.of("username", username, "password", password))
                .post("/api/auth/login");
    }

    @When("I request the protected chat endpoint with authentication")
    public void iRequestProtectedChatWithAuth() {
        lastResponse = baseRequest()
                .header("Authorization", "Bearer " + jwtToken)
                .get("/api/chat/query");
    }

    /** Exposed for other step classes sharing the same Cucumber scenario scope. */
    public String getJwtToken() { return jwtToken; }

    /** Allows other step classes to share their last response with the assertion steps. */
    public void setLastResponse(Response response) { this.lastResponse = response; }

    public Response getLastResponse() { return lastResponse; }

    // ── Then ───────────────────────────────────────────────────────────────

    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShouldBe(int expectedStatus) {
        assertThat(lastResponse.statusCode()).isEqualTo(expectedStatus);
    }

    @Then("the response status code should not be {int}")
    public void theResponseStatusCodeShouldNotBe(int unexpectedStatus) {
        assertThat(lastResponse.statusCode()).isNotEqualTo(unexpectedStatus);
    }

    @And("the response should contain {string} equal to {string}")
    public void theResponseShouldContainFieldEqualTo(String field, String expected) {
        String actual = lastResponse.jsonPath().getString(field);
        assertThat(actual).isEqualTo(expected);
    }

    @And("the response should contain a {string} field")
    public void theResponseShouldContainField(String field) {
        assertThat((Object) lastResponse.jsonPath().get(field)).isNotNull();
    }

    @And("the actuator response should contain status {string}")
    public void theActuatorResponseShouldContainStatus(String expectedStatus) {
        String status = lastResponse.jsonPath().getString("status");
        assertThat(status).isEqualTo(expectedStatus);
    }

    @And("the response should contain a non-empty {string} field")
    public void theResponseShouldContainNonEmptyField(String field) {
        String value = lastResponse.jsonPath().getString(field);
        assertThat(value).isNotNull().isNotBlank();
    }
}
