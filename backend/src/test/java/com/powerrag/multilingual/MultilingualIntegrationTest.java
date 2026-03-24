package com.powerrag.multilingual;

import com.powerrag.infrastructure.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for multilingual support against a real PostgreSQL Testcontainer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("Multilingual Integration Tests")
class MultilingualIntegrationTest {

    @MockitoBean
    AnthropicChatModel anthropicChatModel;

    @Autowired UserPreferenceService userPreferenceService;
    @Autowired LanguageDetector      languageDetector;
    @Autowired MultilingualPromptBuilder promptBuilder;

    // ── UserPreferenceService with real DB ───────────────────────────────────

    @Test
    @DisplayName("Admin user defaults to preferred language 'en'")
    void adminUser_defaultPreferredLanguage_isEn() {
        UserPreferencesResponse resp = userPreferenceService.getPreferences("admin");
        assertThat(resp.preferredLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("Updating preferred language to zh-CN persists and is retrievable")
    void updateLanguage_zhCN_persistsAndRetrievable() {
        userPreferenceService.updateLanguage("admin", "zh-CN");

        UserPreferencesResponse resp = userPreferenceService.getPreferences("admin");
        assertThat(resp.preferredLanguage()).isEqualTo("zh-CN");

        // Reset back to en so other tests are not affected
        userPreferenceService.updateLanguage("admin", "en");
    }

    @Test
    @DisplayName("Unsupported language update throws IllegalArgumentException")
    void updateLanguage_unsupported_throws() {
        assertThatThrownBy(() -> userPreferenceService.updateLanguage("admin", "klingon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Non-existent user throws IllegalArgumentException")
    void getPreferences_unknownUser_throws() {
        assertThatThrownBy(() -> userPreferenceService.getPreferences("ghost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── LanguageDetector ─────────────────────────────────────────────────────

    @Test
    @DisplayName("LanguageDetector: English text returns en")
    void languageDetector_english_returnsEn() {
        assertThat(languageDetector.detect("What is Retrieval Augmented Generation?")).isEqualTo("en");
    }

    @Test
    @DisplayName("LanguageDetector: Chinese text returns zh-CN")
    void languageDetector_chinese_returnsZhCN() {
        assertThat(languageDetector.detect("什么是检索增强生成？")).isEqualTo("zh-CN");
    }

    // ── MultilingualPromptBuilder ────────────────────────────────────────────

    @Test
    @DisplayName("Prompt builder produces Chinese instruction for zh-CN")
    void promptBuilder_zhCN_containsChineseInstruction() {
        String msg = promptBuilder.buildUserMessage("什么是RAG？", "上下文", "zh-CN");
        assertThat(msg).contains("中文");
    }

    @Test
    @DisplayName("Prompt builder produces English instruction for en")
    void promptBuilder_en_containsEnglishInstruction() {
        String msg = promptBuilder.buildUserMessage("What is RAG?", "Context", "en");
        assertThat(msg).contains("English");
    }
}
