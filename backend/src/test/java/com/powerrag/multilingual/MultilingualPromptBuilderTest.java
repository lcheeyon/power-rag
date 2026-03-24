package com.powerrag.multilingual;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MultilingualPromptBuilder Unit Tests")
class MultilingualPromptBuilderTest {

    MultilingualPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new MultilingualPromptBuilder();
    }

    // ── buildLangInstruction ─────────────────────────────────────────────────

    @Test
    @DisplayName("English instruction for lang=en")
    void langInstruction_en_containsEnglish() {
        assertThat(builder.buildLangInstruction("en")).contains("English");
    }

    @Test
    @DisplayName("Chinese instruction for lang=zh-CN")
    void langInstruction_zhCN_containsChinese() {
        String instruction = builder.buildLangInstruction("zh-CN");
        assertThat(instruction).contains("中文");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh-CN", "zh_CN", "zh", "ZH-CN"})
    @DisplayName("Chinese instruction for all zh variants")
    void langInstruction_zhVariants_containsChinese(String lang) {
        assertThat(builder.buildLangInstruction(lang)).contains("中文");
    }

    @Test
    @DisplayName("English instruction for null language")
    void langInstruction_null_defaultsToEnglish() {
        assertThat(builder.buildLangInstruction(null)).contains("English");
    }

    @Test
    @DisplayName("English instruction for unknown language")
    void langInstruction_unknown_defaultsToEnglish() {
        assertThat(builder.buildLangInstruction("fr")).contains("English");
    }

    // ── buildUserMessage ─────────────────────────────────────────────────────

    @Test
    @DisplayName("English prompt contains question, context, and SOURCE citation instruction")
    void buildUserMessage_en_containsContextAndCitationInstruction() {
        String msg = builder.buildUserMessage("What is RAG?", "Context text here", "en");

        assertThat(msg).contains("Context text here");
        assertThat(msg).contains("What is RAG?");
        assertThat(msg).contains("[SOURCE N]");
        assertThat(msg).contains("English");
    }

    @Test
    @DisplayName("Chinese prompt contains Chinese instruction")
    void buildUserMessage_zhCN_containsChineseInstruction() {
        String msg = builder.buildUserMessage("什么是RAG？", "上下文内容", "zh-CN");

        assertThat(msg).contains("上下文内容");
        assertThat(msg).contains("什么是RAG？");
        assertThat(msg).contains("中文");
    }

    @Test
    @DisplayName("No-context prompt still includes question and language instruction")
    void buildUserMessage_noContext_includesQuestionAndLangInstruction() {
        String msg = builder.buildUserMessage("What is RAG?", "", "en");

        assertThat(msg).contains("What is RAG?");
        assertThat(msg).contains("English");
        assertThat(msg).doesNotContain("[SOURCE N]");
    }

    @Test
    @DisplayName("Null context treated same as blank")
    void buildUserMessage_nullContext_includesQuestion() {
        String msg = builder.buildUserMessage("Test question", null, "en");
        assertThat(msg).contains("Test question");
    }
}
