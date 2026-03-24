package com.powerrag.multilingual;

import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Manages per-user language preferences, persisted in the {@code users.preferred_language} column.
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    /** Languages currently supported by the multilingual pipeline. */
    static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "zh-CN");

    private final UserRepository userRepository;

    /**
     * Returns the preferred language for the given user.
     *
     * @throws IllegalArgumentException if the user does not exist
     */
    public UserPreferencesResponse getPreferences(String username) {
        User user = findUser(username);
        return new UserPreferencesResponse(user.getPreferredLanguage());
    }

    /**
     * Updates the preferred language for the given user.
     *
     * @throws IllegalArgumentException if the language is not supported or the user does not exist
     */
    @Transactional
    public UserPreferencesResponse updateLanguage(String username, String language) {
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException(
                    "Unsupported language: '" + language + "'. Supported: " + SUPPORTED_LANGUAGES);
        }
        User user = findUser(username);
        user.setPreferredLanguage(language);
        userRepository.save(user);
        return new UserPreferencesResponse(language);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
