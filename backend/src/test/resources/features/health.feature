Feature: System Health and Availability
  As a system operator
  I want to verify that all health endpoints respond correctly
  So that I can confirm the system is operational

  Background:
    Given the Power RAG application is running

  Scenario: Application health endpoint returns UP status
    When I request the application health endpoint
    Then the response status code should be 200
    And the response should contain "status" equal to "UP"
    And the response should contain "service" equal to "power-rag"
    And the response should contain a "timestamp" field

  Scenario: Application version endpoint returns version information
    When I request the application version endpoint
    Then the response status code should be 200
    And the response should contain "version" equal to "1.0.0-SNAPSHOT"
    And the response should contain "name" equal to "Power RAG"

  Scenario: Spring Boot Actuator health endpoint is accessible
    When I request the actuator health endpoint
    Then the response status code should be 200
    And the actuator response should contain status "UP"

  Scenario: Protected endpoints require authentication
    When I request the protected chat endpoint without authentication
    Then the response status code should be 401

  Scenario: Admin endpoints require authentication
    When I request the admin interactions endpoint without authentication
    Then the response status code should be 401

  Scenario: Login with valid credentials returns a JWT token
    Given a user with username "admin" and password "Admin@1234" exists
    When I send a login request with username "admin" and password "Admin@1234"
    Then the response status code should be 200
    And the response should contain a non-empty "token" field

  Scenario: Login with invalid credentials returns 401
    When I send a login request with username "admin" and password "wrongpassword"
    Then the response status code should be 401

  Scenario: Accessing protected endpoint with valid JWT succeeds
    Given a user with username "admin" and password "Admin@1234" exists
    And I have a valid JWT token for user "admin"
    When I request the protected chat endpoint with authentication
    Then the response status code should not be 401
