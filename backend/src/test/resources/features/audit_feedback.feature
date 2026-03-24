Feature: Interaction Audit and Feedback
  As an administrator or user
  I want to view interaction history and submit ratings
  So that the system can be monitored and continuously improved

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"
    And the guardrail model classifies input as safe

  Scenario: Admin can retrieve paginated interaction list
    When I send a RAG query "What is retrieval augmented generation?"
    And I request the admin interactions endpoint
    Then the admin interactions response status should be 200
    And the admin interactions response should contain interactions

  Scenario: Admin interactions endpoint requires authentication
    When I request the admin interactions endpoint without authentication
    Then the response status code should be 401

  Scenario: User can submit a star rating for an interaction
    When I send a RAG query "Explain embeddings to me"
    And I submit a 5-star rating for the last interaction
    Then the feedback response status should be 201

  Scenario: Duplicate feedback is rejected with conflict
    When I send a RAG query "Tell me about vector databases"
    And I submit a 4-star rating for the last interaction
    And I submit a 4-star rating for the last interaction again
    Then the feedback response status should be 409
