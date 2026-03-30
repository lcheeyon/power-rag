package com.powerrag.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live calls to Atlassian Jira Cloud REST API v3. Validates the same credentials used by the
 * {@code powerrag-tools} MCP server ({@code JIRA_CLOUD_EMAIL} + {@code JIRA_CLOUD_API_TOKEN}).
 * <p>
 * The class is <strong>disabled</strong> unless both variables are non-blank, so {@code mvn test}
 * in CI without secrets does not fail.
 * <p>
 * Local run with repo {@code .env} (from repo root):
 * {@code set -a && source .env && set +a && cd backend && mvn test -Dtest=JiraCloudIntegrationTest}
 */
@DisplayName("Jira Cloud REST (integration)")
@EnabledIf(value = "com.powerrag.mcp.JiraCloudIntegrationTest#jiraCredentialsPresent",
        disabledReason = "Set JIRA_CLOUD_EMAIL and JIRA_CLOUD_API_TOKEN (optional JIRA_CLOUD_BASE_URL)")
class JiraCloudIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    static boolean jiraCredentialsPresent() {
        return notBlank(System.getenv("JIRA_CLOUD_API_TOKEN")) && notBlank(System.getenv("JIRA_CLOUD_EMAIL"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String jiraBaseUrl() {
        String base = System.getenv("JIRA_CLOUD_BASE_URL");
        if (base == null || base.isBlank()) {
            return "https://powerrag.atlassian.net";
        }
        return base.replaceAll("/+$", "");
    }

    private static String basicAuthHeader() {
        String email = System.getenv("JIRA_CLOUD_EMAIL").strip();
        String token = System.getenv("JIRA_CLOUD_API_TOKEN").strip();
        String raw = email + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("GET /rest/api/3/search/jql (project KAN) returns 200 and issue payloads with summary")
    void searchKanProject_returnsStructuredIssues() throws Exception {
        String jql = "project = KAN ORDER BY created DESC";
        String fieldNames = "summary,status,description";
        String query = "jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&maxResults=10"
                + "&fields=" + URLEncoder.encode(fieldNames, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jiraBaseUrl() + "/rest/api/3/search/jql?" + query))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", basicAuthHeader())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode())
                .as("Jira search failed; body (truncated): %s", truncate(response.body(), 800))
                .isEqualTo(200);

        JsonNode root = MAPPER.readTree(response.body());
        assertThat(root.has("issues")).as("response should contain 'issues' array").isTrue();
        assertThat(root.get("issues").isArray()).isTrue();

        for (JsonNode issue : root.get("issues")) {
            assertThat(issue.has("key")).as("each issue should have key").isTrue();
            assertThat(issue.get("key").asText()).isNotBlank();
            JsonNode issueFields = issue.get("fields");
            assertThat(issueFields).as("issue %s should have fields", issue.get("key").asText()).isNotNull();
            assertThat(issueFields.has("summary")).as("issue %s should expose summary", issue.get("key").asText()).isTrue();
        }
    }

    @Test
    @DisplayName("GET /rest/api/3/issue/{key} returns summary when at least one KAN issue exists")
    void getFirstKanIssue_whenPresent_returnsDetail() throws Exception {
        String jql = "project = KAN ORDER BY created DESC";
        String searchQuery = "jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&maxResults=1"
                + "&fields=" + URLEncoder.encode("summary", StandardCharsets.UTF_8);

        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(jiraBaseUrl() + "/rest/api/3/search/jql?" + searchQuery))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", basicAuthHeader())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> searchResp = HTTP.send(searchReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(searchResp.statusCode())
                .as("search for first KAN issue failed; body (truncated): %s", truncate(searchResp.body(), 800))
                .isEqualTo(200);

        JsonNode issues = MAPPER.readTree(searchResp.body()).get("issues");
        if (issues == null || issues.isEmpty()) {
            // Auth and JQL are valid; project may simply have no issues.
            return;
        }

        String key = issues.get(0).get("key").asText();

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(jiraBaseUrl() + "/rest/api/3/issue/" + key + "?fields=summary,status,description"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", basicAuthHeader())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> getResp = HTTP.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(getResp.statusCode())
                .as("GET issue %s failed; body (truncated): %s", key, truncate(getResp.body(), 800))
                .isEqualTo(200);

        JsonNode issue = MAPPER.readTree(getResp.body());
        assertThat(issue.get("key").asText()).isEqualTo(key);
        assertThat(issue.get("fields").get("summary").asText()).isNotBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
