package com.powerrag.multilingual;

import org.springframework.stereotype.Component;

/**
 * Detects the primary language of a text string using Unicode script analysis.
 *
 * <p>Strategy: if ≥10% of codepoints belong to a CJK/Kana script, the text is
 * classified as {@code zh-CN}; otherwise it defaults to {@code en}.
 *
 * <p>The detector intentionally covers only the two languages supported in Phase 6
 * (English and Simplified Chinese). Additional languages can be added later.
 */
@Component
public class LanguageDetector {

    private static final double CJK_THRESHOLD = 0.10;

    /**
     * Detects the language of the given text.
     *
     * @param text the input text (may be null or blank)
     * @return {@code "zh-CN"} if the text appears to be Chinese, {@code "en"} otherwise
     */
    public String detect(String text) {
        if (text == null || text.isBlank()) return "en";

        long total = text.codePoints().count();
        if (total == 0) return "en";

        long cjk = text.codePoints()
                .filter(LanguageDetector::isCjk)
                .count();

        return (double) cjk / total >= CJK_THRESHOLD ? "zh-CN" : "en";
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
