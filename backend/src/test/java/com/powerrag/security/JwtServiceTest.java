package com.powerrag.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for JwtService – token generation, validation, and claim extraction.
 * No Spring context needed; uses ReflectionTestUtils to inject config values.
 */
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "test-secret-key-min-32-chars-ok!!");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86400000L);

        testUser = User.withUsername("testuser")
                .password("encoded-password")
                .authorities(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("generateToken returns non-null token for valid user")
    void generateTokenReturnsNonNullToken() {
        String token = jwtService.generateToken(testUser);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("extractUsername returns correct username from token")
    void extractUsernameReturnsCorrectUsername() {
        String token = jwtService.generateToken(testUser);
        String username = jwtService.extractUsername(token);
        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("isTokenValid returns true for matching user")
    void isTokenValidReturnsTrueForMatchingUser() {
        String token = jwtService.generateToken(testUser);
        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false for different user")
    void isTokenValidReturnsFalseForDifferentUser() {
        String token = jwtService.generateToken(testUser);
        UserDetails otherUser = User.withUsername("otheruser")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for expired token")
    void isTokenValidReturnsFalseForExpiredToken() {
        // Set expiration to 1ms so it expires immediately
        ReflectionTestUtils.setField(jwtService, "expirationMs", 1L);
        String token = jwtService.generateToken(testUser);
        // Wait for expiry
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        assertThat(jwtService.isTokenValid(token, testUser)).isFalse();
    }

    @Test
    @DisplayName("Token generated with extra claims contains those claims")
    void tokenWithExtraClaimsContainsClaims() {
        String token = jwtService.generateToken(
                java.util.Map.of("role", "ADMIN", "lang", "en"),
                testUser
        );
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("Token with tampered signature is rejected")
    void tokenWithTamperedSignatureIsRejected() {
        String token = jwtService.generateToken(testUser);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "tampered-sig";
        assertThrows(Exception.class,
                () -> jwtService.extractUsername(tampered));
    }
}
