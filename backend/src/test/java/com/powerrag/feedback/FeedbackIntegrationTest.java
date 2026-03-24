package com.powerrag.feedback;

import com.powerrag.domain.Feedback;
import com.powerrag.domain.FeedbackRepository;
import com.powerrag.domain.Interaction;
import com.powerrag.domain.InteractionRepository;
import com.powerrag.domain.UserRepository;
import com.powerrag.infrastructure.TestContainersConfig;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Phase 8: Interaction Audit + Feedback.
 * Uses a real PostgreSQL Testcontainer; LLM models are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("Feedback Integration Tests")
class FeedbackIntegrationTest {

    @MockitoBean AnthropicChatModel anthropicChatModel;
    @MockitoBean OllamaChatModel    ollamaChatModel;

    @Autowired FeedbackService       feedbackService;
    @Autowired FeedbackRepository    feedbackRepository;
    @Autowired InteractionRepository interactionRepository;
    @Autowired UserRepository        userRepository;

    @BeforeEach
    void setUp() {
        feedbackRepository.deleteAll();
        interactionRepository.deleteAll();
    }

    private Interaction seedInteraction() {
        return interactionRepository.save(Interaction.builder()
                .sessionId(UUID.randomUUID())
                .queryText("What is RAG?")
                .queryLanguage("en")
                .responseText("RAG stands for Retrieval-Augmented Generation.")
                .responseLanguage("en")
                .modelProvider("ANTHROPIC")
                .modelId("claude-sonnet-4-6")
                .confidence(0.9)
                .sources(List.of())
                .cacheHit(false)
                .build());
    }

    @Test
    @DisplayName("Submitted feedback is persisted with correct fields")
    void submitFeedback_persistedWithCorrectFields() {
        Interaction interaction = seedInteraction();

        FeedbackResponse response = feedbackService.submit(
                interaction.getId(), null, 5, "Very helpful!");

        assertThat(response.id()).isNotNull();
        assertThat(response.interactionId()).isEqualTo(interaction.getId());
        assertThat(response.userId()).isNull();
        assertThat(response.starRating()).isEqualTo((short) 5);
        assertThat(response.comment()).isEqualTo("Very helpful!");
        assertThat(response.createdAt()).isNotNull();

        List<Feedback> saved = feedbackRepository.findByInteractionId(interaction.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStarRating()).isEqualTo((short) 5);
    }

    @Test
    @DisplayName("Duplicate feedback from same user throws DuplicateFeedbackException")
    void submitFeedback_duplicate_throwsException() {
        Interaction interaction = seedInteraction();
        UUID userId = userRepository.findByUsername("admin").orElseThrow().getId();

        feedbackService.submit(interaction.getId(), userId, 4, "Good");

        assertThatThrownBy(() -> feedbackService.submit(interaction.getId(), userId, 3, "Changed mind"))
                .isInstanceOf(DuplicateFeedbackException.class);
    }

    @Test
    @DisplayName("Different users can submit feedback on the same interaction")
    void submitFeedback_differentUsers_bothPersisted() {
        Interaction interaction = seedInteraction();

        feedbackService.submit(interaction.getId(), null, 5, "Great");
        feedbackService.submit(interaction.getId(), null, 2, "Not great");

        List<FeedbackResponse> results = feedbackService.getByInteraction(interaction.getId());
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Feedback for unknown interaction throws EntityNotFoundException")
    void submitFeedback_unknownInteraction_throwsEntityNotFound() {
        assertThatThrownBy(() -> feedbackService.submit(UUID.randomUUID(), UUID.randomUUID(), 3, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Admin interactions page returns newest-first ordering")
    void adminInteractions_returnedNewestFirst() {
        seedInteraction();
        seedInteraction();

        Page<Interaction> page = interactionRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        List<Interaction> content = page.getContent();
        for (int i = 0; i < content.size() - 1; i++) {
            assertThat(content.get(i).getCreatedAt())
                    .isAfterOrEqualTo(content.get(i + 1).getCreatedAt());
        }
    }

    @Test
    @DisplayName("getByInteraction returns empty list when no feedback exists")
    void getByInteraction_noFeedback_returnsEmpty() {
        Interaction interaction = seedInteraction();
        assertThat(feedbackService.getByInteraction(interaction.getId())).isEmpty();
    }
}
