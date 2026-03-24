package com.powerrag.rag.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Set;

/**
 * Detects image-generation intent and generates images via the Google GenAI SDK.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Try {@code imagen-3.0-generate-002} (dedicated, highest quality).</li>
 *   <li>Fall back to {@code gemini-2.0-flash-preview-image-generation} with
 *       {@code responseModalities=IMAGE} if Imagen is unavailable on the API tier.</li>
 * </ol>
 */
@Slf4j
@Service
public class ImageGenerationService {

    private static final Set<String> GEN_VERBS   = Set.of(
            "generate", "create", "draw", "paint", "make", "produce", "render", "design", "sketch");
    private static final Set<String> IMAGE_NOUNS = Set.of(
            "image", "picture", "photo", "illustration", "artwork", "painting",
            "drawing", "diagram", "portrait", "logo", "icon");

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    // ── Public API ────────────────────────────────────────────────────────

    public boolean isImageGenerationRequest(String question) {
        if (question == null || question.isBlank()) return false;
        String lower = question.toLowerCase();
        boolean hasVerb = GEN_VERBS.stream().anyMatch(lower::contains);
        boolean hasNoun = IMAGE_NOUNS.stream().anyMatch(lower::contains);
        return hasVerb && hasNoun;
    }

    /**
     * Generates an image for {@code prompt}.
     * Returns a data-URL (e.g. {@code "data:image/png;base64,..."}) or {@code null} on failure.
     */
    public String generateImage(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GOOGLE_API_KEY not set — image generation skipped");
            return null;
        }
        String result = tryImagen(prompt);
        if (result == null) result = tryGeminiImageOut(prompt);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Tries the dedicated Imagen model (best quality). */
    private String tryImagen(String prompt) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            GenerateImagesResponse resp = client.models.generateImages(
                    "imagen-3.0-generate-002",
                    prompt,
                    GenerateImagesConfig.builder()
                            .numberOfImages(1)
                            .outputMimeType("image/png")
                            .build());

            return resp.generatedImages()
                    .filter(imgs -> !imgs.isEmpty())
                    .map(imgs -> imgs.get(0))
                    .flatMap(gi -> gi.image())
                    .flatMap(img -> img.imageBytes())
                    .map(bytes -> "data:image/png;base64,"
                            + Base64.getEncoder().encodeToString(bytes))
                    .orElse(null);

        } catch (Exception e) {
            log.warn("Imagen generation failed ({}), trying Gemini fallback", e.getMessage());
            return null;
        }
    }

    /** Falls back to Gemini multimodal output (IMAGE response modality). */
    private String tryGeminiImageOut(String prompt) {
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            GenerateContentResponse resp = client.models.generateContent(
                    "gemini-2.0-flash-preview-image-generation",
                    prompt,
                    GenerateContentConfig.builder()
                            .responseModalities("IMAGE", "TEXT")
                            .build());

            // resp.parts() is a convenience flat-list across all candidates → content → parts
            return resp.parts().stream()
                    .filter(p -> p.inlineData().isPresent())
                    .map(p -> p.inlineData().get())
                    .filter(blob -> blob.data().isPresent())
                    .findFirst()
                    .map(blob -> {
                        String mime = blob.mimeType().orElse("image/png");
                        return "data:" + mime + ";base64,"
                                + Base64.getEncoder().encodeToString(blob.data().get());
                    })
                    .orElse(null);

        } catch (Exception e) {
            log.error("Gemini image generation failed: {}", e.getMessage());
            return null;
        }
    }
}
