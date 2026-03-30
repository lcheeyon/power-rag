package com.powerrag.rag.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.powerrag.config.ImageGenerationProperties;
import com.powerrag.rag.model.ImageGenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects image-generation intent and generates images via the Google GenAI SDK.
 *
 * <p>Uses the Nano Banana family first ({@code gemini-3-pro-image-preview} “Nano Banana Pro”,
 * then faster {@code gemini-3.1-flash-image-preview}, {@code gemini-2.5-flash-image}), then
 * Imagen, then a legacy Gemini image model.
 */
@Slf4j
@Service
public class ImageGenerationService {

    private static final Set<String> GEN_VERBS = Set.of(
            "generate", "create", "draw", "paint", "make", "produce", "render", "design", "sketch",
            "illustrate", "visualize", "show me", "give me");
    private static final Set<String> IMAGE_NOUNS = Set.of(
            "image", "picture", "photo", "illustration", "artwork", "painting",
            "drawing", "diagram", "portrait", "logo", "icon", "wallpaper", "banner",
            "graphic", "visual", "thumbnail", "cover", "mockup", "infographic", "comic",
            "meme", "sticker", "emoji", "scene", "poster", "sprite");

    /** e.g. "draw me a cat", "paint a sunset" */
    private static final Pattern DIRECT_ART = Pattern.compile(
            "\\b(draw|paint|sketch|illustrate)\\b(\\s+me)?\\s+(a|an|the)?\\s*\\S+");

    private final ImageGenerationProperties props;
    private final String                    apiKey;

    public ImageGenerationService(
            ImageGenerationProperties props,
            @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.props  = props;
        this.apiKey = apiKey != null ? apiKey.strip() : "";
    }

    public boolean fallbackToLlmOnFailure() {
        return props.fallbackToLlmOnFailure();
    }

    public boolean isImageGenerationRequest(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase();
        boolean hasVerb = GEN_VERBS.stream().anyMatch(v -> phraseOrWord(lower, v));
        boolean hasNoun = IMAGE_NOUNS.stream().anyMatch(lower::contains);
        if (hasVerb && hasNoun) {
            return true;
        }
        if (DIRECT_ART.matcher(lower).find()) {
            return true;
        }
        // "nano banana" product prompt
        return lower.contains("nano banana");
    }

    /** Match multi-word phrases like "show me" as substring. */
    private static boolean phraseOrWord(String lower, String verb) {
        return lower.contains(verb.trim().toLowerCase());
    }

    /**
     * @param preferredGeminiImageModelId when the user picked a Gemini image model in the UI, try it first
     */
    public ImageGenerationResult generateImage(String prompt, String preferredGeminiImageModelId) {
        if (apiKey.isBlank()) {
            log.warn("GOOGLE_API_KEY not set — image generation skipped");
            return null;
        }
        LinkedHashSet<String> geminiOrder = new LinkedHashSet<>();
        if (preferredGeminiImageModelId != null && !preferredGeminiImageModelId.isBlank()
                && isGeminiNativeImageModelId(preferredGeminiImageModelId)) {
            geminiOrder.add(preferredGeminiImageModelId.strip());
        }
        for (String id : props.geminiNativeModels()) {
            if (id != null && !id.isBlank()) {
                geminiOrder.add(id.strip());
            }
        }
        for (String modelId : geminiOrder) {
            ImageGenerationResult r = tryGeminiNativeImage(modelId, prompt);
            if (r != null && r.ok()) {
                return r;
            }
        }
        ImageGenerationResult imagen = tryImagen(props.imagenModel(), prompt);
        if (imagen != null && imagen.ok()) {
            return imagen;
        }
        return tryGeminiNativeImage(props.legacyGeminiModel(), prompt);
    }

    public ImageGenerationResult generateImage(String prompt) {
        return generateImage(prompt, null);
    }

    private static boolean isGeminiNativeImageModelId(String id) {
        String lower = id.toLowerCase();
        return lower.contains("image") || lower.contains("imagen") || lower.contains("flash-image");
    }

    private ImageGenerationResult tryGeminiNativeImage(String modelId, String prompt) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            GenerateContentResponse resp = client.models.generateContent(
                    modelId,
                    prompt,
                    GenerateContentConfig.builder()
                            .responseModalities("IMAGE", "TEXT")
                            .build());

            String dataUrl = resp.parts().stream()
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

            if (dataUrl != null) {
                return new ImageGenerationResult(dataUrl, modelId);
            }
        } catch (Exception e) {
            log.warn("Gemini native image model {} failed: {}", modelId, e.getMessage());
        }
        return null;
    }

    private ImageGenerationResult tryImagen(String modelId, String prompt) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        try (Client client = Client.builder().apiKey(apiKey).build()) {
            GenerateImagesResponse resp = client.models.generateImages(
                    modelId,
                    prompt,
                    GenerateImagesConfig.builder()
                            .numberOfImages(1)
                            .outputMimeType("image/png")
                            .build());

            String dataUrl = resp.generatedImages()
                    .filter(imgs -> !imgs.isEmpty())
                    .map(imgs -> imgs.get(0))
                    .flatMap(gi -> gi.image())
                    .flatMap(img -> img.imageBytes())
                    .map(bytes -> "data:image/png;base64,"
                            + Base64.getEncoder().encodeToString(bytes))
                    .orElse(null);

            if (dataUrl != null) {
                return new ImageGenerationResult(dataUrl, modelId);
            }
        } catch (Exception e) {
            log.warn("Imagen model {} failed: {}", modelId, e.getMessage());
        }
        return null;
    }
}
