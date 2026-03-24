Feature: Text-to-SQL
  As a user
  I want to ask natural language questions about structured data
  So that I can retrieve information without writing SQL

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"

  Scenario: Natural language question generates and executes a valid SELECT
    Given the SQL LLM will return "SELECT COUNT(*) FROM documents"
    When I send a SQL query "How many documents have been indexed?"
    Then the SQL response status should be 200
    And the SQL response should contain a "rowCount" field

  Scenario: Question requiring DELETE is rejected with an error
    Given the SQL LLM will return "DELETE FROM users WHERE username = 'test'"
    When I send a SQL query "Delete all test users from the system"
    Then the SQL response status should be 400
    And the SQL error response should contain "Only SELECT"

  Scenario: Ambiguous question results in a clarification response
    Given the SQL LLM will return "Could you please specify which table you are querying?"
    When I send a SQL query "Show me the data"
    Then the SQL response status should be 200
    And the SQL response "clarification" field should not be blank

  Scenario: Unauthenticated SQL query is rejected
    When I send a SQL query without authentication "How many users?"
    Then the SQL response status should be 401
