package com.powerrag.api;

import com.powerrag.domain.InteractionRepository;
import com.powerrag.feedback.FeedbackResponse;
import com.powerrag.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Admin-only endpoints (secured at path level in SecurityConfig via /api/admin/**).
 * Provides paginated interaction history and per-interaction feedback.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final InteractionRepository interactionRepository;
    private final FeedbackService       feedbackService;

    @GetMapping("/interactions")
    public Page<InteractionSummary> listInteractions(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return interactionRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(InteractionSummary::from);
    }

    @GetMapping("/interactions/{id}")
    public ResponseEntity<InteractionSummary> getInteraction(@PathVariable UUID id) {
        return interactionRepository.findById(id)
                .map(InteractionSummary::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Interaction not found"));
    }

    @GetMapping("/interactions/{id}/feedback")
    public List<FeedbackResponse> getFeedbackForInteraction(@PathVariable UUID id) {
        return feedbackService.getByInteraction(id);
    }
}
