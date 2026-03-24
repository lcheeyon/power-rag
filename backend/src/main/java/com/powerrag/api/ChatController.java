package com.powerrag.api;

import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import com.powerrag.rag.model.RagResponse;
import com.powerrag.rag.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Chat / RAG query endpoint.
 * GET  /api/chat/query — lightweight probe kept for the health-check BDD scenario.
 * POST /api/chat/query — full RAG pipeline via RagService.
 *
 * <p>Language resolution order:
 * <ol>
 *   <li>Explicit {@code language} field in the request body</li>
 *   <li>Authenticated user's {@code preferredLanguage} setting</li>
 *   <li>Default: {@code "en"}</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService     ragService;
    private final UserRepository userRepository;

    /** Retained for the existing auth-check BDD scenario (GET with JWT → not 401). */
    @GetMapping("/query")
    public ResponseEntity<Map<String, String>> queryProbe() {
        return ResponseEntity.ok(Map.of("status", "OK", "message", "Use POST for RAG queries"));
    }

    @PostMapping("/query")
    public ResponseEntity<RagResponse> query(
            @RequestBody @Valid ChatQueryRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User   user      = userRepository.findByUsername(principal.getUsername()).orElse(null);
        UUID   sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID();
        String language  = resolveLanguage(request.language(), user);

        RagResponse response = ragService.query(request.question(), request.imageBase64(),
                sessionId, user, language, request.modelProvider(), request.modelId());
        return ResponseEntity.ok(response);
    }

    /** Returns the explicit request language, then the user's preference, then "en". */
    private String resolveLanguage(String requestLanguage, User user) {
        if (requestLanguage != null && !requestLanguage.isBlank()) return requestLanguage;
        if (user != null && user.getPreferredLanguage() != null
                && !user.getPreferredLanguage().isBlank()) {
            return user.getPreferredLanguage();
        }
        return "en";
    }
}
