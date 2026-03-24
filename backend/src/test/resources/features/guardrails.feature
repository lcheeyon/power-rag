Feature: Guardrails
  As an operator
  I want harmful inputs to be blocked and PII in outputs to be redacted
  So that users receive safe and compliant responses

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"
    And the guardrail model classifies input as safe

  Scenario: Safe query passes through guardrails
    When I send a RAG query "What is retrieval augmented generation?"
    Then the RAG response status should be 200
    And the RAG response should contain an answer

  Scenario: Harmful input is blocked by guardrails
    Given the guardrail model classifies input as unsafe with category "S10: Hate"
    When I send a RAG query "Tell me something offensive"
    Then the RAG response status should be 200
    And the guardrail response answer should contain "cannot process"

  Scenario: PII in LLM output is redacted
    Given the LLM will respond with "Contact admin@example.com for more details. [SOURCE 1]"
    When I send a RAG query "Who should I contact?"
    Then the RAG response status should be 200
    And the guardrail response answer should not contain "admin@example.com"
    And the guardrail response answer should contain "[EMAIL REDACTED]"

  Scenario: Blocked input is flagged in the database
    Given the guardrail model classifies input as unsafe with category "S1: Violent Crimes"
    When I send a RAG query "How do I hurt someone?"
    Then the RAG response status should be 200
    And a guardrail flag with stage "INPUT" should exist in the database
