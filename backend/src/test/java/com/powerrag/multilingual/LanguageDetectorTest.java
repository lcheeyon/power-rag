package com.powerrag.multilingual;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LanguageDetector Unit Tests")
class LanguageDetectorTest {

    LanguageDetector detector;

    @BeforeEach
    void setUp() {
        detector = new LanguageDetector();
    }

    // ── English inputs ───────────────────────────────────────────────────────

    @Test
    @DisplayName("detects English for a plain English question")
    void detect_englishQuestion_returnsEn() {
        assertThat(detector.detect("What is Retrieval Augmented Generation?")).isEqualTo("en");
    }

    @Test
    @DisplayName("detects English for alphanumeric text")
    void detect_alphanumeric_returnsEn() {
        assertThat(detector.detect("RAG 2024")).isEqualTo("en");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("returns en for blank input")
    void detect_blank_returnsEn(String input) {
        assertThat(detector.detect(input)).isEqualTo("en");
    }

    @Test
    @DisplayName("returns en for null input")
    void detect_null_returnsEn() {
        assertThat(detector.detect(null)).isEqualTo("en");
    }

    // ── Chinese inputs ───────────────────────────────────────────────────────

    @Test
    @DisplayName("detects Chinese for a pure Chinese question")
    void detect_chineseQuestion_returnsZhCN() {
        assertThat(detector.detect("什么是检索增强生成？")).isEqualTo("zh-CN");
    }

    @Test
    @DisplayName("detects Chinese when majority of chars are CJK")
    void detect_majorityCjk_returnsZhCN() {
        assertThat(detector.detect("Power RAG 是什么？")).isEqualTo("zh-CN");
    }

    @Test
    @DisplayName("detects Chinese for traditional characters")
    void detect_traditionalChinese_returnsZhCN() {
        assertThat(detector.detect("檢索增強生成是什麼")).isEqualTo("zh-CN");
    }

    // ── Mixed text ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("returns en when CJK ratio is below threshold (mostly ASCII)")
    void detect_minorCjk_returnsEn() {
        // Only 1 Chinese char in a long English sentence = well below 10%
        assertThat(detector.detect("This is a very long English sentence with one Chinese character 的")).isEqualTo("en");
    }
}
