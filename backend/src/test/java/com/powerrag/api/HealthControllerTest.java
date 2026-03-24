package com.powerrag.api;

import com.powerrag.infrastructure.TestContainersConfig;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.WebApplicationContext;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the HealthController.
 * Uses REST Assured MockMvc – no real HTTP server required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("HealthController Integration Tests")
class HealthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext);
    }

    @Test
    @DisplayName("GET /api/public/health returns 200 with UP status")
    void getHealthReturns200WithUpStatus() {
        given()
            .when()
                .get("/api/public/health")
            .then()
                .statusCode(HttpStatus.OK.value())
                .body("status",    equalTo("UP"))
                .body("service",   equalTo("power-rag"))
                .body("timestamp", notNullValue());
    }

    @Test
    @DisplayName("GET /api/public/version returns app version info")
    void getVersionReturnsAppVersionInfo() {
        given()
            .when()
                .get("/api/public/version")
            .then()
                .statusCode(HttpStatus.OK.value())
                .body("version", equalTo("1.0.0-SNAPSHOT"))
                .body("name",    equalTo("Power RAG"));
    }

    @Test
    @DisplayName("GET /actuator/health returns Spring Boot health status")
    void getActuatorHealthReturnsSpringBootHealthStatus() {
        given()
            .when()
                .get("/actuator/health")
            .then()
                .statusCode(HttpStatus.OK.value())
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("GET /api/chat/query without auth returns 401")
    void getProtectedEndpointWithoutAuthReturns401() {
        given()
            .when()
                .get("/api/chat/query")
            .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("GET /api/admin/interactions without auth returns 401")
    void getAdminEndpointWithoutAuthReturns401() {
        given()
            .when()
                .get("/api/admin/interactions")
            .then()
                .statusCode(HttpStatus.UNAUTHORIZED.value());
    }
}
