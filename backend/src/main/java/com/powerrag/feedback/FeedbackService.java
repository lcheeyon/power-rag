package com.powerrag.feedback;

import com.powerrag.domain.Feedback;
import com.powerrag.domain.FeedbackRepository;
import com.powerrag.domain.InteractionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository    feedbackRepository;
    private final InteractionRepository interactionRepository;

    @Transactional
    public FeedbackResponse submit(UUID interactionId, UUID userId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (!interactionRepository.existsById(interactionId)) {
            throw new EntityNotFoundException("Interaction not found: " + interactionId);
        }
        if (userId != null && feedbackRepository.existsByInteractionIdAndUserId(interactionId, userId)) {
            throw new DuplicateFeedbackException("Feedback already submitted for this interaction");
        }
        Feedback saved = feedbackRepository.saveAndFlush(Feedback.builder()
                .interactionId(interactionId)
                .userId(userId)
                .starRating((short) rating)
                .comment(comment)
                .build());
        return FeedbackResponse.from(saved);
    }

    public List<FeedbackResponse> getByInteraction(UUID interactionId) {
        return feedbackRepository.findByInteractionId(interactionId).stream()
                .map(FeedbackResponse::from)
                .toList();
    }
}
