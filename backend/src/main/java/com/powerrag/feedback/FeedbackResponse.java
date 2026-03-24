package com.powerrag.feedback;

import com.powerrag.domain.Feedback;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID    id,
        UUID    interactionId,
        UUID    userId,
        Short   starRating,
        Short   thumbs,
        String  comment,
        Instant createdAt) {

    public static FeedbackResponse from(Feedback f) {
        return new FeedbackResponse(
                f.getId(), f.getInteractionId(), f.getUserId(),
                f.getStarRating(), f.getThumbs(), f.getComment(), f.getCreatedAt());
    }
}
