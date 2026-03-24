Feature: Semantic Cache Integration
  As a user
  I want repeated RAG queries to be served efficiently
  So that latency is minimised and cost is reduced

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"

  Scenario: RAG response always contains a cacheHit field
    Given a document has been ingested for RAG
    When I send a RAG query "What is the semantic cache?"
    Then the RAG response status should be 200
    And the RAG response should contain a "cacheHit" field

  Scenario: First query is not a cache hit when cache is disabled in test profile
    Given a document has been ingested for RAG
    When I send a RAG query "Explain semantic caching for RAG"
    Then the RAG response status should be 200
    And the RAG response "cacheHit" field should be false

  Scenario: Unauthenticated cache query is rejected
    When I send a RAG query without authentication "What is vector similarity?"
    Then the RAG response status should be 401
