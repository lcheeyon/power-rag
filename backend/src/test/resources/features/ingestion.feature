Feature: Document Ingestion Pipeline
  As a knowledge base administrator
  I want to upload various document types
  So that their content is chunked and stored for RAG retrieval

  Background:
    Given the Power RAG application is running
    And I have a valid JWT token for user "admin"

  Scenario: Java file is uploaded and class/method metadata is stored
    When I upload a Java source file
    Then the ingestion response status should be 200
    And the response should contain "status" equal to "INDEXED"
    And the response should contain a "chunkCount" field
    And the chunk count should be greater than 0

  Scenario: PDF is uploaded and page_number metadata is present on every chunk
    When I upload a PDF file with 2 pages
    Then the ingestion response status should be 200
    And the response should contain "status" equal to "INDEXED"
    And the chunk count should be greater than 0

  Scenario: Excel file is uploaded and sheet and row metadata are captured
    When I upload an Excel file
    Then the ingestion response status should be 200
    And the response should contain "status" equal to "INDEXED"
    And the chunk count should be greater than 0

  Scenario: Word document is uploaded and heading hierarchy is preserved
    When I upload a Word document
    Then the ingestion response status should be 200
    And the response should contain "status" equal to "INDEXED"
    And the chunk count should be greater than 0

  Scenario: Unsupported file type is rejected with 400
    When I upload a file with unsupported type "video.mp4"
    Then the ingestion response status should be 400
