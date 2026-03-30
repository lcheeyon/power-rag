package com.powerrag.rag.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryIntentClassifierTest {

    private final QueryIntentClassifier classifier = new QueryIntentClassifier(new ObjectMapper());

    @Test
    void extractJsonObject_stripsProseAroundJson() {
        assertThat(QueryIntentClassifier.extractJsonObject("Here: {\"retrieveDocuments\":true,\"allowMcpTools\":false}"))
                .isEqualTo("{\"retrieveDocuments\":true,\"allowMcpTools\":false}");
    }

    @Test
    void parseModelJson_readsBooleans() {
        QueryIntent i = classifier.parseModelJson(
                "{\"retrieveDocuments\":false,\"allowMcpTools\":true}", "question");
        assertThat(i.retrieveDocuments()).isFalse();
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void parseModelJson_urlInQuestion_forcesMcp() {
        QueryIntent i = classifier.parseModelJson(
                "{\"retrieveDocuments\":true,\"allowMcpTools\":false}",
                "Summarize https://example.com/page for me");
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void fallback_withUrl_enablesMcp() {
        QueryIntent i = classifier.fallback("open http://test.dev/x");
        assertThat(i.retrieveDocuments()).isTrue();
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void fallback_noUrl_mcpOff() {
        QueryIntent i = classifier.fallback("What is 2+2?");
        assertThat(i.retrieveDocuments()).isTrue();
        assertThat(i.allowMcpTools()).isFalse();
    }

    @Test
    void parseModelJson_weatherInQuestion_forcesMcp() {
        QueryIntent i = classifier.parseModelJson(
                "{\"retrieveDocuments\":true,\"allowMcpTools\":false}",
                "What is the weather in Paris tomorrow?");
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void fallback_jiraMentions_enablesMcp() {
        QueryIntent i = classifier.fallback("List open tickets on Jira for project KAN");
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void fallback_issueKey_enablesMcp() {
        QueryIntent i = classifier.fallback("Summarize KAN-42 for me");
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void questionImpliesMcpTools_timeQuery() {
        assertThat(QueryIntentClassifier.questionImpliesMcpTools("What time is it in Tokyo?")).isTrue();
    }

    @Test
    void fallback_githubWording_enablesMcp() {
        QueryIntent i = classifier.fallback("Search the codebase on GitHub for RagService");
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void fallback_gcpLoggingWording_enablesMcp() {
        QueryIntent i = classifier.fallback("Show errors from GCP logs for Cloud Run");
        assertThat(i.allowMcpTools()).isTrue();
    }

    @Test
    void fallback_emailWording_enablesMcp() {
        QueryIntent i = classifier.fallback("Summarize my unread email from today");
        assertThat(i.allowMcpTools()).isTrue();
    }
}
