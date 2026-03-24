Feature: RAG Query Pipeline
  As an authenticated user
  I want to ask questions about the knowledge base
  So that I receive cited answers from ingested documents

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"

  Scenario: Authenticated user receives an answer with sources
    Given a document has been ingested for RAG
    When I send a RAG query "What is Power RAG?"
    Then the RAG response status should be 200
    And the RAG response should contain an answer
    And the RAG response should contain a confidence score

  Scenario: Unauthenticated RAG query is rejected
    When I send a RAG query without authentication "What is this?"
    Then the RAG response status should be 401

  Scenario: Valid query returns modelId in response
    Given a document has been ingested for RAG
    When I send a RAG query "Describe the main class"
    Then the RAG response status should be 200
    And the RAG response should contain a "modelId" field
