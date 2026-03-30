package com.powerrag.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists Gemini models from the <a href="https://ai.google.dev/api/rest/v1beta/models/list">Google AI Gemini API</a>
 * ({@code generativelanguage.googleapis.com}), using the same API key as Spring AI chat.
 */
@Slf4j
@Service
public class GeminiModelCatalogService {

    private static final String LIST_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

    private final String           apiKey;
    private final RestClient       restClient;
    private final ObjectMapper     objectMapper;

    public GeminiModelCatalogService(
            @Value("${spring.ai.google.genai.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey       = apiKey != null ? apiKey.strip() : "";
        this.objectMapper = objectMapper;
        this.restClient   = RestClient.builder().build();
    }

    /**
     * Chat-capable Gemini models ({@code generateContent}), excluding non-{@code gemini*} IDs.
     */
    public List<GeminiModelInfo> listChatModels() {
        if (apiKey.isEmpty()) {
            log.debug("Gemini model list skipped: spring.ai.google.genai.api-key is empty");
            return List.of();
        }
        try {
            Map<String, GeminiModelInfo> byId = new LinkedHashMap<>();
            String                       pageToken = null;
            do {
                String url = buildListUrl(pageToken);
                String raw = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(String.class);
                if (raw == null || raw.isBlank()) {
                    break;
                }
                JsonNode root = objectMapper.readTree(raw);
                JsonNode models = root.get("models");
                if (models != null && models.isArray()) {
                    for (JsonNode m : models) {
                        if (!supportsGenerateContent(m)) {
                            continue;
                        }
                        String modelId = resolveModelId(m);
                        if (modelId == null || !modelId.startsWith("gemini")) {
                            continue;
                        }
                        if (isEmbeddingOnlyName(modelId)) {
                            continue;
                        }
                        String display = textOrEmpty(m.get("displayName"));
                        if (display.isEmpty()) {
                            display = modelId;
                        }
                        String desc = textOrEmpty(m.get("description"));
                        boolean mm = guessMultimodal(modelId, desc);
                        byId.putIfAbsent(modelId, new GeminiModelInfo(modelId, display, desc, mm));
                    }
                }
                JsonNode next = root.get("nextPageToken");
                pageToken = next != null && next.isTextual() ? next.asText() : null;
            } while (pageToken != null && !pageToken.isBlank());

            List<GeminiModelInfo> list = new ArrayList<>(byId.values());
            list.sort(Comparator.comparing(GeminiModelInfo::modelId, geminiModelOrder()));
            log.info("Gemini API model catalog: {} chat models", list.size());
            return list;
        } catch (RestClientException e) {
            log.warn("Gemini models.list HTTP failed: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Gemini models.list parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildListUrl(String pageToken) {
        String encKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(LIST_BASE)
                .append("?key=").append(encKey)
                .append("&pageSize=100");
        if (pageToken != null && !pageToken.isBlank()) {
            sb.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static boolean supportsGenerateContent(JsonNode m) {
        JsonNode methods = m.get("supportedGenerationMethods");
        if (methods == null || !methods.isArray()) {
            return false;
        }
        for (JsonNode x : methods) {
            if (!x.isTextual()) {
                continue;
            }
            String s = x.asText();
            if ("generateContent".equals(s) || "GENERATE_CONTENT".equals(s)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveModelId(JsonNode m) {
        JsonNode base = m.get("baseModelId");
        if (base != null && base.isTextual() && !base.asText().isBlank()) {
            return base.asText().strip();
        }
        JsonNode name = m.get("name");
        if (name != null && name.isTextual()) {
            String n = name.asText();
            if (n.startsWith("models/")) {
                return n.substring("models/".length()).strip();
            }
            return n.strip();
        }
        return null;
    }

    private static String textOrEmpty(JsonNode n) {
        return n != null && n.isTextual() ? n.asText().strip() : "";
    }

    private static boolean isEmbeddingOnlyName(String modelId) {
        String lower = modelId.toLowerCase();
        return lower.contains("embedding") || lower.contains("embed-");
    }

    private static boolean guessMultimodal(String modelId, String description) {
        String blob = (modelId + " " + description).toLowerCase();
        if (blob.contains("embedding")) {
            return false;
        }
        return true;
    }

    /** Prefer Gemini 3.x, then 2.5, then lexical. */
    private static Comparator<String> geminiModelOrder() {
        return (a, b) -> {
            int pa = prefixRank(a);
            int pb = prefixRank(b);
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            return a.compareToIgnoreCase(b);
        };
    }

    private static int prefixRank(String id) {
        String lower = id.toLowerCase();
        if (lower.startsWith("gemini-3")) {
            return 0;
        }
        if (lower.startsWith("gemini-2.5") || lower.startsWith("gemini-2-")) {
            return 1;
        }
        if (lower.startsWith("gemini-exp") || lower.contains("preview")) {
            return 2;
        }
        return 3;
    }
}
