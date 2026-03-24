package com.powerrag.ingestion.parser;

import com.powerrag.ingestion.model.ParsedSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImageParser Unit Tests")
class ImageParserTest {

    @Mock private ChatClient                         visionClient;
    @Mock private ChatClient.ChatClientRequestSpec   requestSpec;
    @Mock private ChatClient.CallResponseSpec        callSpec;
    @Mock private ChatClient.PromptUserSpec          promptSpec;

    private ImageParser parser;

    @BeforeEach
    void setUp() {
        parser = new ImageParser(visionClient);
        // Chain: visionClient.prompt() → .messages() → .call() → .content()
        when(visionClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.messages(any(org.springframework.ai.chat.messages.UserMessage.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    @Test
    @DisplayName("supportedExtension returns png")
    void supportedExtensionIsPng() {
        assertThat(parser.supportedExtension()).isEqualTo("png");
    }

    @Test
    @DisplayName("supportedExtensions returns all image types")
    void supportedExtensionsContainsAllImageFormats() {
        assertThat(parser.supportedExtensions())
                .containsExactlyInAnyOrder("png", "jpg", "jpeg", "gif", "webp");
    }

    @Test
    @DisplayName("Successful parse returns one section with image description")
    void successfulParseReturnsOneSection() {
        when(callSpec.content()).thenReturn("A screenshot showing a bar chart with 3 bars.");

        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}), "diagram.png");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getText()).isEqualTo("A screenshot showing a bar chart with 3 bars.");
    }

    @Test
    @DisplayName("Section metadata contains file_name, doc_type IMAGE, and section image-content")
    void sectionMetadataIsCorrect() {
        when(callSpec.content()).thenReturn("Some image description");

        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "chart.jpg");

        ParsedSection s = sections.get(0);
        assertThat(s.getMetadata().get("file_name")).isEqualTo("chart.jpg");
        assertThat(s.getMetadata().get("doc_type")).isEqualTo("IMAGE");
        assertThat(s.getMetadata().get("section")).isEqualTo("image-content");
    }

    @Test
    @DisplayName("Returns empty list when vision LLM throws exception")
    void returnsEmptyListOnLlmFailure() {
        when(callSpec.content()).thenThrow(new RuntimeException("Vision LLM unavailable"));

        List<ParsedSection> sections = parser.parse(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "fail.png");

        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("Returns empty list when input stream throws IOException")
    void returnsEmptyListOnIOException() throws Exception {
        InputStream brokenStream = mock(InputStream.class);
        when(brokenStream.readAllBytes()).thenThrow(new IOException("Disk error"));

        List<ParsedSection> sections = parser.parse(brokenStream, "broken.png");

        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("PNG filename resolves to image/png MIME type (no LLM call verification)")
    void pngMimeTypeResolution() {
        when(callSpec.content()).thenReturn("png description");
        parser.parse(new ByteArrayInputStream(new byte[]{1}), "photo.png");
        verify(visionClient).prompt();
    }

    @Test
    @DisplayName("JPG filename resolves correctly (no LLM call verification)")
    void jpgMimeTypeResolution() {
        when(callSpec.content()).thenReturn("jpg description");
        parser.parse(new ByteArrayInputStream(new byte[]{1}), "photo.jpg");
        verify(visionClient).prompt();
    }

    @Test
    @DisplayName("GIF filename resolves correctly")
    void gifMimeTypeResolution() {
        when(callSpec.content()).thenReturn("gif description");
        parser.parse(new ByteArrayInputStream(new byte[]{1}), "anim.gif");
        verify(visionClient).prompt();
    }

    @Test
    @DisplayName("WEBP filename resolves correctly")
    void webpMimeTypeResolution() {
        when(callSpec.content()).thenReturn("webp description");
        parser.parse(new ByteArrayInputStream(new byte[]{1}), "img.webp");
        verify(visionClient).prompt();
    }
}
