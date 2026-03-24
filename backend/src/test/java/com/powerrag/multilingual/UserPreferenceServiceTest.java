package com.powerrag.multilingual;

import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferenceService Unit Tests")
class UserPreferenceServiceTest {

    @Mock UserRepository userRepository;

    UserPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new UserPreferenceService(userRepository);
    }

    private User userWith(String lang) {
        return User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hash")
                .preferredLanguage(lang)
                .build();
    }

    // ── getPreferences ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPreferences returns current preferred language")
    void getPreferences_returnsCurrentLanguage() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWith("zh-CN")));

        UserPreferencesResponse resp = service.getPreferences("alice");

        assertThat(resp.preferredLanguage()).isEqualTo("zh-CN");
    }

    @Test
    @DisplayName("getPreferences throws when user not found")
    void getPreferences_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPreferences("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }

    // ── updateLanguage ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateLanguage persists new language and returns it")
    void updateLanguage_valid_savesAndReturns() {
        User user = userWith("en");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesResponse resp = service.updateLanguage("alice", "zh-CN");

        assertThat(resp.preferredLanguage()).isEqualTo("zh-CN");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPreferredLanguage()).isEqualTo("zh-CN");
    }

    @Test
    @DisplayName("updateLanguage to en succeeds")
    void updateLanguage_toEn_succeeds() {
        User user = userWith("zh-CN");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesResponse resp = service.updateLanguage("alice", "en");

        assertThat(resp.preferredLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("updateLanguage with unsupported language throws")
    void updateLanguage_unsupported_throws() {
        assertThatThrownBy(() -> service.updateLanguage("alice", "klingon"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("klingon");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("supported languages includes en and zh-CN")
    void supportedLanguages_containsExpected() {
        assertThat(UserPreferenceService.SUPPORTED_LANGUAGES).contains("en", "zh-CN");
    }
}
