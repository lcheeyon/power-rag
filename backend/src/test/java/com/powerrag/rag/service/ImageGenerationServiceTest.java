package com.powerrag.rag.service;

import com.powerrag.config.ImageGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImageGenerationServiceTest {

    private ImageGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ImageGenerationService(
                new ImageGenerationProperties(null, null, null, false),
                "");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Generate an image of a red barn",
            "Create a logo for my café",
            "Draw me a cartoon owl",
            "Paint a sunset over the ocean",
            "Sketch a city skyline",
            "nano banana style portrait of a chef"
    })
    @DisplayName("isImageGenerationRequest is true for common image prompts")
    void detectsImageIntent(String q) {
        assertThat(service.isImageGenerationRequest(q)).isTrue();
    }

    @Test
    @DisplayName("isImageGenerationRequest is false for normal RAG questions")
    void rejectsNonImageQueries() {
        assertThat(service.isImageGenerationRequest("What is retrieval-augmented generation?")).isFalse();
        assertThat(service.isImageGenerationRequest(null)).isFalse();
        assertThat(service.isImageGenerationRequest("   ")).isFalse();
    }
}
