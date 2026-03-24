package com.powerrag.api;

import com.powerrag.domain.Interaction;

import java.time.Instant;
import java.util.UUID;

public record InteractionSummary(
        UUID    id,
        UUID    sessionId,
        String  queryText,
        String  modelProvider,
        String  modelId,
        Double  confidence,
        boolean cacheHit,
        boolean guardrailFlag,
        String  flagReason,
        Integer durationMs,
        Instant createdAt) {

    public static InteractionSummary from(Interaction i) {
        return new InteractionSummary(
                i.getId(), i.getSessionId(), i.getQueryText(),
                i.getModelProvider(), i.getModelId(), i.getConfidence(),
                i.isCacheHit(), i.isGuardrailFlag(), i.getFlagReason(),
                i.getDurationMs(), i.getCreatedAt());
    }
}
