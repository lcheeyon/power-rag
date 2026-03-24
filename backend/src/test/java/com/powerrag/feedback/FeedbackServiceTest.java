package com.powerrag.feedback;

import com.powerrag.domain.Feedback;
import com.powerrag.domain.FeedbackRepository;
import com.powerrag.domain.InteractionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService Unit Tests")
class FeedbackServiceTest {

    @Mock FeedbackRepository    feedbackRepository;
    @Mock InteractionRepository interactionRepository;

    FeedbackService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(feedbackRepository, interactionRepository);
    }

    private Feedback buildFeedback(UUID interactionId, UUID userId, int starRating, String comment) {
        return Feedback.builder()
                .id(UUID.randomUUID())
                .interactionId(interactionId)
                .userId(userId)
                .starRating((short) starRating)
                .comment(comment)
                .createdAt(Instant.now())
                .build();
    }

    private void mockSave() {
        when(feedbackRepository.saveAndFlush(any(Feedback.class))).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            return f;
        });
    }

    @Test
    @DisplayName("Valid rating is persisted and FeedbackResponse returned")
    void submit_validRating_returnsFeedbackResponse() {
        UUID interactionId = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();

        when(interactionRepository.existsById(interactionId)).thenReturn(true);
        when(feedbackRepository.existsByInteractionIdAndUserId(interactionId, userId)).thenReturn(false);
        mockSave();

        FeedbackResponse response = service.submit(interactionId, userId, 4, "Great answer!");

        assertThat(response.starRating()).isEqualTo((short) 4);
        assertThat(response.comment()).isEqualTo("Great answer!");
        assertThat(response.interactionId()).isEqualTo(interactionId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.id()).isNotNull();
    }

    @Test
    @DisplayName("Rating below 1 throws IllegalArgumentException")
    void submit_ratingBelowMin_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), UUID.randomUUID(), 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 5");
    }

    @Test
    @DisplayName("Rating above 5 throws IllegalArgumentException")
    void submit_ratingAboveMax_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), UUID.randomUUID(), 6, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 5");
    }

    @Test
    @DisplayName("Unknown interaction throws EntityNotFoundException")
    void submit_interactionNotFound_throwsEntityNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(interactionRepository.existsById(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(unknownId, UUID.randomUUID(), 3, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("Duplicate feedback for same user and interaction throws DuplicateFeedbackException")
    void submit_duplicate_throwsDuplicateFeedbackException() {
        UUID interactionId = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();

        when(interactionRepository.existsById(interactionId)).thenReturn(true);
        when(feedbackRepository.existsByInteractionIdAndUserId(interactionId, userId)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(interactionId, userId, 5, "Love it"))
                .isInstanceOf(DuplicateFeedbackException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    @DisplayName("Null userId bypasses duplicate check")
    void submit_nullUserId_skipsDuplicateCheck() {
        UUID interactionId = UUID.randomUUID();

        when(interactionRepository.existsById(interactionId)).thenReturn(true);
        mockSave();

        FeedbackResponse response = service.submit(interactionId, null, 3, null);

        assertThat(response.userId()).isNull();
        verify(feedbackRepository, never()).existsByInteractionIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("getByInteraction returns all feedback for an interaction")
    void getByInteraction_returnsAllFeedback() {
        UUID interactionId = UUID.randomUUID();
        List<Feedback> feedbacks = List.of(
                buildFeedback(interactionId, UUID.randomUUID(), 5, "Excellent"),
                buildFeedback(interactionId, UUID.randomUUID(), 3, "OK"));
        when(feedbackRepository.findByInteractionId(interactionId)).thenReturn(feedbacks);

        List<FeedbackResponse> results = service.getByInteraction(interactionId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).starRating()).isEqualTo((short) 5);
        assertThat(results.get(1).starRating()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("getByInteraction returns empty list when no feedback exists")
    void getByInteraction_noFeedback_returnsEmpty() {
        UUID interactionId = UUID.randomUUID();
        when(feedbackRepository.findByInteractionId(interactionId)).thenReturn(List.of());

        assertThat(service.getByInteraction(interactionId)).isEmpty();
    }

    @Test
    @DisplayName("Feedback with null comment is persisted successfully")
    void submit_nullComment_persistedSuccessfully() {
        UUID interactionId = UUID.randomUUID();
        UUID userId        = UUID.randomUUID();

        when(interactionRepository.existsById(interactionId)).thenReturn(true);
        when(feedbackRepository.existsByInteractionIdAndUserId(interactionId, userId)).thenReturn(false);
        mockSave();

        FeedbackResponse response = service.submit(interactionId, userId, 2, null);

        assertThat(response.comment()).isNull();
        assertThat(response.starRating()).isEqualTo((short) 2);
    }
}
