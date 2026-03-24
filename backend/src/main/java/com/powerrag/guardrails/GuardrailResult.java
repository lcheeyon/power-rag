package com.powerrag.guardrails;

/**
 * Immutable result from a guardrail check.
 * {@code safe=true} means the content passed; {@code safe=false} means it was flagged.
 */
public record GuardrailResult(boolean passed, String category) {

    public static GuardrailResult safe() {
        return new GuardrailResult(true, null);
    }

    public static GuardrailResult unsafe(String category) {
        return new GuardrailResult(false, category != null ? category : "POLICY_VIOLATION");
    }
}
