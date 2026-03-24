package com.powerrag.bdd.steps;

import io.cucumber.java.en.And;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for cache.feature BDD scenarios.
 */
public class CacheSteps {

    @Autowired
    private HealthCheckSteps healthCheckSteps;

    @And("the RAG response {string} field should be false")
    public void ragResponseFieldShouldBeFalse(String field) {
        Response lastResponse = healthCheckSteps.getLastResponse();
        Boolean value = lastResponse.jsonPath().getBoolean(field);
        assertThat(value).isFalse();
    }
}
