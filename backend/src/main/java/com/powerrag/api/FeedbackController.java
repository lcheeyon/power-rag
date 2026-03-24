package com.powerrag.api;

import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import com.powerrag.feedback.FeedbackRequest;
import com.powerrag.feedback.FeedbackResponse;
import com.powerrag.feedback.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User-facing endpoint for submitting star ratings on completed interactions.
 * POST /api/interactions/{id}/feedback — authenticated users submit 1–5 star ratings.
 */
@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final UserRepository  userRepository;

    @PostMapping("/{id}/feedback")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable UUID id,
            @RequestBody @Valid FeedbackRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User user = userRepository.findByUsername(principal.getUsername()).orElse(null);
        UUID userId = user != null ? user.getId() : null;

        FeedbackResponse response = feedbackService.submit(id, userId, request.rating(), request.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
