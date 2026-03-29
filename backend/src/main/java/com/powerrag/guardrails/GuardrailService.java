package com.powerrag.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Core guardrail logic:
 * <ul>
 *   <li>Input safety: calls Google Gemini (fast tier, default {@code gemini-2.5-flash}) via Spring AI; fails open on error.</li>
 *   <li>Output PII: pure regex detection and redaction (no LLM call).</li>
 *   <li>Flag logging: persists to {@code guardrail_flags} in a separate transaction.</li>
 * </ul>
 */
@Slf4j
@Service
public class GuardrailService {

    // ── PII patterns ────────────────────────────────────────────────────────

    private static final Pattern EMAIL =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern SSN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern PHONE =
            Pattern.compile("\\b(\\+?1[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}\\b");
    private static final Pattern CREDIT_CARD =
            Pattern.compile("\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b");

    private static final int RAW_CONTENT_MAX_LEN = 500;

    @Value("${powerrag.guardrails.enabled:true}")
    private boolean guardrailsEnabled;

    private final ChatClient              geminiGuardClient;
    private final GuardrailFlagRepository flagRepository;
    private final String                  inputModelId;

    public GuardrailService(@Qualifier("geminiGuard") ChatClient geminiGuardClient,
                            GuardrailFlagRepository flagRepository,
                            @Value("${powerrag.guardrails.input-model-id:gemini-2.5-flash}") String inputModelId) {
        this.geminiGuardClient = geminiGuardClient;
        this.flagRepository    = flagRepository;
        this.inputModelId      = inputModelId;
    }

    /** Model ID used for input safety classification (e.g. for audit logs). */
    public String inputModelId() {
        return inputModelId;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Calls Gemini to classify the user input against a short safety rubric.
     * Returns {@link GuardrailResult#safe()} on any error (fail-open).
     */
    public GuardrailResult checkInput(String text) {
        if (!guardrailsEnabled || text == null || text.isBlank()) return GuardrailResult.safe();
        try {
            String response = geminiGuardClient.prompt()
                    .user(buildGuardrailPrompt(text))
                    .options(GoogleGenAiChatOptions.builder()
                            .model(inputModelId)
                            .temperature(0.0)
                            .build())
                    .call()
                    .content();
            return parseGuardrailResponse(response);
        } catch (Exception e) {
            log.warn("Guardrail input check failed, allowing through: {}", e.getMessage());
            return GuardrailResult.safe();
        }
    }

    /**
     * Regex-based PII detection in LLM output. No LLM call needed.
     */
    public GuardrailResult checkOutput(String text) {
        if (text == null || text.isBlank()) return GuardrailResult.safe();
        if (EMAIL.matcher(text).find())       return GuardrailResult.unsafe("PII_EMAIL");
        if (SSN.matcher(text).find())         return GuardrailResult.unsafe("PII_SSN");
        if (PHONE.matcher(text).find())       return GuardrailResult.unsafe("PII_PHONE");
        if (CREDIT_CARD.matcher(text).find()) return GuardrailResult.unsafe("PII_CREDIT_CARD");
        return GuardrailResult.safe();
    }

    /** Replaces PII matches with redaction tokens. */
    public String redactPii(String text) {
        if (text == null) return null;
        text = EMAIL.matcher(text).replaceAll("[EMAIL REDACTED]");
        text = SSN.matcher(text).replaceAll("[SSN REDACTED]");
        text = PHONE.matcher(text).replaceAll("[PHONE REDACTED]");
        text = CREDIT_CARD.matcher(text).replaceAll("[CARD REDACTED]");
        return text;
    }

    /**
     * Persists a guardrail flag.
     * Uses {@code REQUIRES_NEW} so the record commits even if the caller's transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GuardrailFlag logFlag(UUID interactionId, String stage,
                                 String ruleTriggered, String severity, String rawContent) {
        String truncated = rawContent != null && rawContent.length() > RAW_CONTENT_MAX_LEN
                ? rawContent.substring(0, RAW_CONTENT_MAX_LEN)
                : rawContent;
        return flagRepository.save(GuardrailFlag.builder()
                .interactionId(interactionId)
                .stage(stage)
                .ruleTriggered(ruleTriggered != null ? ruleTriggered : "POLICY_VIOLATION")
                .severity(severity)
                .rawContent(truncated)
                .build());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String buildGuardrailPrompt(String text) {
        return "Evaluate if the following user message violates content safety policies. "
                + "Categories include violence, hate speech, illegal activities, self-harm, "
                + "discrimination, and exploitation. "
                + "Reply with ONLY the single word \"safe\", OR exactly two lines: first line \"unsafe\", "
                + "second line the violated category. No other explanation.\n"
                + "Message: " + text;
    }

    private GuardrailResult parseGuardrailResponse(String response) {
        if (response == null) return GuardrailResult.safe();
        java.util.List<String> lines = response.strip().lines()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        if (lines.isEmpty()) return GuardrailResult.safe();

        String first = lines.get(0).toLowerCase(Locale.ROOT);
        if (first.startsWith("unsafe")) {
            if (lines.size() >= 2) {
                return GuardrailResult.unsafe(lines.get(1));
            }
            String tail = lines.get(0).length() > "unsafe".length()
                    ? lines.get(0).substring("unsafe".length()).strip().replaceFirst("^[:,-]\\s*", "")
                    : "";
            return GuardrailResult.unsafe(tail.isEmpty() ? "POLICY_VIOLATION" : tail);
        }
        if (first.equals("safe") || first.startsWith("safe")) {
            return GuardrailResult.safe();
        }
        return GuardrailResult.safe();
    }
}
