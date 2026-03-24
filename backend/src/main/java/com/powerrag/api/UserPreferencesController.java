package com.powerrag.api;

import com.powerrag.multilingual.UserPreferenceService;
import com.powerrag.multilingual.UserPreferencesRequest;
import com.powerrag.multilingual.UserPreferencesResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * User language preference endpoints.
 * GET  /api/user/preferences — retrieve current preferred language
 * PUT  /api/user/preferences — update preferred language
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping("/preferences")
    public ResponseEntity<UserPreferencesResponse> getPreferences(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(userPreferenceService.getPreferences(principal.getUsername()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @RequestBody @Valid UserPreferencesRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                userPreferenceService.updateLanguage(principal.getUsername(), request.preferredLanguage()));
    }
}
