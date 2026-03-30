package com.powerrag.rag.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * One lightweight LLM call to decide whether to search the KB and whether MCP tools are needed.
 */
@Slf4j
@Component
public class QueryIntentClassifier {

    private static final String ROUTER_SYSTEM = """
            You route questions for a RAG assistant with optional MCP tools (web fetch, time, weather, Jira, GitHub code/repo, GCP Cloud Logging, mailbox IMAP/SMTP).
            Reply with ONLY one JSON object (no markdown fences, no prose):
            {"retrieveDocuments":true|false,"allowMcpTools":true|false}

            retrieveDocuments — true if the user likely needs their uploaded knowledge base (policies, internal docs, PDFs they ingested, "my documents", company-specific material). false for pure general knowledge, small talk, standalone math/logic, generic coding help with no doc context, or questions that clearly do not depend on private uploads.

            allowMcpTools — true if any live tool may help: URL or fetch/read a webpage; current time or timezone; weather or forecast; Jira/support tickets (issue keys, Atlassian); GitHub (search code, read files in a repo, pull requests/commits in a repo); Google Cloud Logging / Stackdriver / production log queries; email/mailbox (inbox, unread, search mail, read message, summarize thread, draft or send reply). false when a static KB or general knowledge answer suffices. Default false; do not enable for purely hypothetical or historical trivia with no live data need.

            If unsure about retrieveDocuments, prefer true.""";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<>\"{}|\\\\^`\\[\\]]+", Pattern.CASE_INSENSITIVE);

    /** Issue keys, Jira wording, or the team's Cloud site. */
    private static final Pattern JIRA_LIVE_PATTERN = Pattern.compile(
            "(?i)\\b([a-z][a-z0-9]+-\\d+|\\bjira\\b|\\batlassian\\b|\\bsupport ticket\\b|\\bkan-\\d+)");

    private static final Pattern WEATHER_LIVE_PATTERN = Pattern.compile(
            "(?i)\\b(weather|forecast|temperature|rain|humidity|wind chill|feels like)\\b");

    private static final Pattern TIME_LIVE_PATTERN = Pattern.compile(
            "(?i)\\b(what time|current time|time now|timezone|time in |utc\\b|gmt\\b|zulu time)\\b");

    /** GitHub code search, repo file reads, PR/commit style questions. */
    private static final Pattern GITHUB_MCP_PATTERN = Pattern.compile(
            "(?i)\\b(github\\b|git\\s+hub|repo:\\s*\\S+/\\S+|code\\s+search|search\\s+(the\\s+)?code(base)?|"
                    + "pull\\s+request|\\bpr\\s*#|\\bcommit(s)?\\b|repository\\s+file|in\\s+the\\s+repo)\\b");

    /** GCP / Stackdriver / Cloud Logging queries. */
    private static final Pattern GCP_LOGGING_MCP_PATTERN = Pattern.compile(
            "(?i)\\b(gcp\\s+logs?|google\\s+cloud\\s+logging|stackdriver|cloud\\s+logging|logs?\\s+explorer|"
                    + "log\\s+entries|severity\\s*>?=|cloud\\s+run\\s+logs|gke\\s+logs)\\b");

    /** Inbox / IMAP-style email questions (boutquin mcp-server-email). */
    private static final Pattern EMAIL_MCP_PATTERN = Pattern.compile(
            "(?i)\\b(email|e-mail|inbox|unread\\s+mail|mailbox|imap|smtp|gmail|outlook\\s+mail|"
                    + "reply\\s+to\\s+(this\\s+)?(mail|message|email)|summarize\\s+(this\\s+)?(mail|message|email)|"
                    + "draft\\s+(an?\\s+)?(email|reply))\\b");

    private final ObjectMapper objectMapper;

    public QueryIntentClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Classifies the user turn using the same chat client family as the main answer (with small, deterministic options).
     */
    public QueryIntent classify(String question, String language, ChatClient client,
                                String modelProvider, String modelId) {
        if (question == null || question.isBlank()) {
            return fallback(question);
        }
        try {
            ChatClient.ChatClientRequestSpec spec = client.prompt()
                    .system(ROUTER_SYSTEM)
                    .user("language=" + (language != null ? language : "en") + "\n\n" + question);
            spec = applyRouterOptions(spec, modelProvider, modelId);
            String raw = spec.call().content();
            return parseModelJson(raw, question);
        } catch (Exception e) {
            log.warn("Intent classification failed, using heuristic fallback: {}", e.getMessage());
            return fallback(question);
        }
    }

    private ChatClient.ChatClientRequestSpec applyRouterOptions(ChatClient.ChatClientRequestSpec spec,
                                                                String provider, String modelId) {
        String p = provider != null ? provider : "ANTHROPIC";
        if ("OLLAMA".equalsIgnoreCase(p) && modelId != null && !modelId.isBlank()) {
            return spec.options(OllamaChatOptions.builder().model(modelId).temperature(0.0).build());
        }
        if ("GEMINI".equalsIgnoreCase(p) && modelId != null && !modelId.isBlank()) {
            return spec.options(GoogleGenAiChatOptions.builder()
                    .model(modelId)
                    .temperature(0.0)
                    .build());
        }
        AnthropicChatOptions.Builder b = AnthropicChatOptions.builder().temperature(0.0).maxTokens(350);
        if (modelId != null && !modelId.isBlank()) {
            b.model(modelId);
        }
        return spec.options(b.build());
    }

    QueryIntent parseModelJson(String raw, String question) {
        if (raw == null || raw.isBlank()) {
            return fallback(question);
        }
        String json = extractJsonObject(raw);
        if (json == null) {
            return fallback(question);
        }
        try {
            RouterPayload payload = objectMapper.readValue(json, RouterPayload.class);
            boolean retrieve = payload.retrieveDocuments != null ? payload.retrieveDocuments : true;
            boolean mcp = Boolean.TRUE.equals(payload.allowMcpTools);
            if (questionImpliesMcpTools(question)) {
                mcp = true;
            }
            return new QueryIntent(retrieve, mcp);
        } catch (Exception e) {
            log.debug("Could not parse intent JSON: {}", json, e);
            return fallback(question);
        }
    }

    static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    QueryIntent fallback(String question) {
        return new QueryIntent(true, questionImpliesMcpTools(question));
    }

    /**
     * Conservative signals that MCP tools (fetch, time, weather, Jira, GitHub, GCP logs) should be attached.
     * Used after router JSON and when the router call fails.
     */
    static boolean questionImpliesMcpTools(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        if (URL_PATTERN.matcher(question).find()) {
            return true;
        }
        if (JIRA_LIVE_PATTERN.matcher(question).find()) {
            return true;
        }
        if (WEATHER_LIVE_PATTERN.matcher(question).find()) {
            return true;
        }
        if (TIME_LIVE_PATTERN.matcher(question).find()) {
            return true;
        }
        if (GITHUB_MCP_PATTERN.matcher(question).find()) {
            return true;
        }
        if (GCP_LOGGING_MCP_PATTERN.matcher(question).find()) {
            return true;
        }
        return EMAIL_MCP_PATTERN.matcher(question).find();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RouterPayload {
        public Boolean retrieveDocuments;
        public Boolean allowMcpTools;
    }
}
