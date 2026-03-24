package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Parses image files (PNG, JPG, GIF, WEBP) by sending them to a vision-capable LLM
 * (Gemini Flash) and indexing the resulting textual description.
 */
@Slf4j
@Component
public class ImageParser implements DocumentParser {

    private static final String VISION_PROMPT =
            "You are an expert document analyst. Extract and describe ALL content from this image "
            + "in structured detail. Include: any visible text (transcribe it exactly), data from "
            + "tables or charts, descriptions of diagrams, key information, and context. "
            + "Be thorough so the content can be searched and retrieved later.";

    private final ChatClient visionClient;

    public ImageParser(@Qualifier("geminiFlash") ChatClient visionClient) {
        this.visionClient = visionClient;
    }

    @Override
    public String supportedExtension() { return "png"; }

    @Override
    public List<String> supportedExtensions() {
        return List.of("png", "jpg", "jpeg", "gif", "webp");
    }

    @Override
    public List<ParsedSection> parse(InputStream input, String fileName) {
        try {
            byte[] imageBytes = input.readAllBytes();
            String mimeType   = resolveMimeType(fileName);

            Media media = new Media(
                    MimeType.valueOf(mimeType),
                    new ByteArrayResource(imageBytes));

            UserMessage message = UserMessage.builder()
                    .text(VISION_PROMPT)
                    .media(media)
                    .build();

            String description = visionClient.prompt()
                    .messages(message)
                    .call()
                    .content();

            log.info("Image '{}' described via vision LLM ({} chars)", fileName, description.length());

            return List.of(ParsedSection.builder()
                    .text(description)
                    .metadata(Map.of(
                            "file_name", fileName,
                            "doc_type",  "IMAGE",
                            "section",   "image-content"
                    ))
                    .build());

        } catch (IOException e) {
            log.error("Failed to read image '{}': {}", fileName, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Vision LLM failed for '{}': {}", fileName, e.getMessage());
            return List.of();
        }
    }

    private String resolveMimeType(String fileName) {
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) return "image/jpeg";
        if (ext.endsWith(".gif"))  return "image/gif";
        if (ext.endsWith(".webp")) return "image/webp";
        return "image/png";
    }
}
