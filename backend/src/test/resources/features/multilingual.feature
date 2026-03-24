Feature: Multilingual Support
  As a user
  I want to interact with Power RAG in my preferred language
  So that I receive responses in the language I understand best

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"

  Scenario: User can update preferred language to zh-CN
    When I update my language preference to "zh-CN"
    Then the preference response status should be 200
    And the preference response should contain language "zh-CN"

  Scenario: User preferred language is used when no language specified in query
    Given I have set my language preference to "zh-CN"
    And the LLM will respond in Chinese with "Power RAG 是一个检索增强生成系统。[SOURCE 1]"
    When I send a RAG query "什么是RAG？"
    Then the RAG response status should be 200
    And the RAG response should contain an answer

  Scenario: Explicit language in request overrides user preference
    Given I have set my language preference to "zh-CN"
    And a document has been ingested for RAG
    When I send a RAG query with language "en" "What is RAG?"
    Then the response status code should be 200

  Scenario: Invalid language preference is rejected
    When I update my language preference to "klingon"
    Then the preference response status should be 400

  Scenario: ZH-CN query and EN query are treated as independent cache entries
    Given I have set my language preference to "en"
    And a document has been ingested for RAG
    When I send a RAG query "What is RAG?"
    Then the RAG response status should be 200
    And the RAG response "cacheHit" field should be false
    When I send a RAG query with language "zh-CN" "What is RAG?"
    Then the response status code should be 200
    And the RAG response "cacheHit" field should be false

  Scenario: Unauthenticated preferences request is rejected
    When I request preferences without authentication
    Then the preference response status should be 401
