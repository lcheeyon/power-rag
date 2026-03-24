package com.powerrag.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Proxies the Ollama /api/tags endpoint to the frontend.
 * Returns only chat-capable models (excludes embedding-only models).
 */
@Slf4j
@RestController
@RequestMapping("/api/models")
public class OllamaModelsController {

    private final RestClient restClient;

    public OllamaModelsController(
            @Value("${powerrag.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(ollamaBaseUrl).build();
    }

    public record OllamaModelInfo(
            String modelId,
            String label,
            boolean multimodal,
            String family,
            String parameterSize
    ) {}

    @GetMapping("/ollama")
    public ResponseEntity<List<OllamaModelInfo>> listOllamaModels() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("models")) {
                return ResponseEntity.ok(List.of());
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) response.get("models");

            List<OllamaModelInfo> chatModels = models.stream()
                    .filter(m -> isChatModel(m))
                    .map(m -> {
                        String name    = (String) m.get("name");
                        String details = detailsString(m, "family");
                        String size    = detailsString(m, "parameter_size");
                        boolean vision = isVisionCapable(name);
                        String label   = formatLabel(name, size);
                        return new OllamaModelInfo(name, label, vision, details, size);
                    })
                    .toList();

            log.debug("Ollama chat models available: {}", chatModels.size());
            return ResponseEntity.ok(chatModels);

        } catch (Exception e) {
            log.warn("Could not reach Ollama at configured URL: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    private boolean isChatModel(Map<String, Object> model) {
        String name = ((String) model.getOrDefault("name", "")).toLowerCase();
        String family = detailsString(model, "family").toLowerCase();
        // Exclude embedding-only models
        return !name.contains("embed") && !family.contains("nomic-bert") && !family.contains("bert");
    }

    private boolean isVisionCapable(String name) {
        String lower = name.toLowerCase();
        return lower.contains("llava") || lower.contains("bakllava")
                || lower.contains("vision") || lower.contains("moondream")
                || lower.contains("minicpm-v");
    }

    @SuppressWarnings("unchecked")
    private String detailsString(Map<String, Object> model, String key) {
        Object details = model.get("details");
        if (details instanceof Map<?, ?> d) {
            Object val = ((Map<String, Object>) d).get(key);
            return val != null ? val.toString() : "";
        }
        return "";
    }

    private String formatLabel(String name, String paramSize) {
        // "qwen2.5-coder:32b" → "Qwen 2.5 Coder (32B)"
        String base = name.contains(":") ? name.substring(0, name.lastIndexOf(':')) : name;
        String formatted = base.replace("-", " ")
                              .replace(".", " ")
                              .replaceAll("\\s+", " ")
                              .trim();
        // Title-case first letter of each word
        String[] words = formatted.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1))
                  .append(" ");
            }
        }
        String label = sb.toString().trim();
        return paramSize.isBlank() ? label : label + " (" + paramSize + ")";
    }
}
