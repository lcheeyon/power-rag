package com.powerrag.multilingual;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Builds language-aware LLM user messages for RAG queries.
 *
 * <p>Appends a language instruction to the assembled context + question
 * so the LLM knows which language to respond in.
 */
@Component
public class MultilingualPromptBuilder {

    /** Safe subset for IANA timezone ids in prompts (e.g. {@code America/Los_Angeles}). */
    private static final Pattern SAFE_TZ = Pattern.compile("^[A-Za-z0-9_+./-]{2,120}$");

    /**
     * Builds the full user message for an LLM call.
     *
     * @param question the user's question
     * @param context  the assembled RAG context (may be blank)
     * @param language the target language tag (e.g. "en", "zh-CN")
     * @return a complete user message ready to pass to the LLM
     */
    public String buildUserMessage(String question, String context, String language) {
        return buildUserMessage(question, context, language, true);
    }

    /**
     * @param hasRelevantContext when false the LLM is instructed to rely solely on its
     *                           own knowledge (no document context is included).
     */
    public String buildUserMessage(String question, String context, String language,
                                   boolean hasRelevantContext) {
        return buildUserMessage(question, context, language, hasRelevantContext, false);
    }

    /**
     * @param hasRelevantContext when false the LLM relies on general knowledge only.
     * @param imagePresent       when true an image has been attached; instructs the LLM to analyse it.
     */
    public String buildUserMessage(String question, String context, String language,
                                   boolean hasRelevantContext, boolean imagePresent) {
        return buildUserMessage(question, context, language, hasRelevantContext, imagePresent, null);
    }

    /**
     * @param clientTimezone optional IANA id from the user's browser for time-tool answers.
     */
    public String buildUserMessage(String question, String context, String language,
                                   boolean hasRelevantContext, boolean imagePresent,
                                   String clientTimezone) {
        String langInstruction = buildLangInstruction(language);
        String imageInstruction = imagePresent
                ? "An image has been attached. Carefully examine it and incorporate your visual analysis into your answer.\n\n"
                : "";
        String tzBlock = browserTimezoneHint(clientTimezone);

        if (!hasRelevantContext || context == null || context.isBlank()) {
            return imageInstruction
                    + "The knowledge base does not contain documents relevant to this question. "
                    + "Answer using your general knowledge.\n\n"
                    + "Question: " + question + "\n\n"
                    + tzBlock
                    + langInstruction;
        }
        return imageInstruction
                + context
                + "\n\nQuestion: " + question
                + "\n\nPrimarily answer using the sources above, citing [SOURCE N] numbers inline. "
                + "If the sources do not contain sufficient information to fully answer the question, "
                + "supplement with your general knowledge and clearly indicate which parts come from "
                + "general knowledge rather than the provided sources.\n\n"
                + tzBlock
                + langInstruction;
    }

    /**
     * Injects a stable hint so the model passes the user's zone into {@code get_current_time}.
     */
    String browserTimezoneHint(String clientTimezone) {
        if (clientTimezone == null || clientTimezone.isBlank()) {
            return "";
        }
        String t = clientTimezone.strip();
        if (!SAFE_TZ.matcher(t).matches()) {
            return "";
        }
        return "[User's browser timezone (IANA): " + t + ". "
                + "For \"what time is it\" or current local date/time without another zone, "
                + "call get_current_time with timezone_name=\"" + t + "\".]\n\n";
    }

    /**
     * Returns the language-specific instruction appended to every prompt.
     * Package-visible for unit testing.
     */
    String buildLangInstruction(String language) {
        if (language != null && (language.equalsIgnoreCase("zh-CN")
                || language.equalsIgnoreCase("zh_CN")
                || language.equalsIgnoreCase("zh"))) {
            return "请用简体中文（Simplified Chinese）回答。";
        }
        return "Respond in English.";
    }
}
