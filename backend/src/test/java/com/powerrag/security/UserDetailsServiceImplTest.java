package com.powerrag.security;

import com.powerrag.domain.User;
import com.powerrag.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl Unit Tests")
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .passwordHash("$2b$10$hashed")
                .email("admin@test.com")
                .roles(Set.of("ADMIN", "USER"))
                .active(true)
                .preferredLanguage("en")
                .build();
    }

    @Test
    @DisplayName("loadUserByUsername returns UserDetails for existing user")
    void loadUserByUsernameReturnsUserDetails() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        UserDetails details = userDetailsService.loadUserByUsername("admin");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("$2b$10$hashed");
        assertThat(details.getAuthorities()).hasSize(2);
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException for unknown user")
    void loadUserByUsernameThrowsForUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("loadUserByUsername sets accountLocked for inactive user")
    void loadUserByUsernameLocksInactiveUser() {
        User inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .username("inactive")
                .passwordHash("hash")
                .email("inactive@test.com")
                .roles(Set.of("USER"))
                .active(false)
                .preferredLanguage("en")
                .build();
        when(userRepository.findByUsername("inactive")).thenReturn(Optional.of(inactiveUser));

        UserDetails details = userDetailsService.loadUserByUsername("inactive");

        assertThat(details.isAccountNonLocked()).isFalse();
    }
}
