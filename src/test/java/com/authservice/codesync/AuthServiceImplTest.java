package com.authservice.codesync;

import com.authservice.codesync.entity.User;
import com.authservice.codesync.repository.UserRepository;
import com.authservice.codesync.security.JwtTokenProvider;
import com.authservice.codesync.service.AuthServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserRepository        userRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock JwtTokenProvider      jwtTokenProvider;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .userId(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("$2a$12$hashed")
                .role(User.Role.DEVELOPER)
                .provider(User.AuthProvider.LOCAL)
                .isActive(true)
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        User result = authService.register("alice", "alice@example.com", "password123", "Alice");

        assertThat(result.getUsername()).isEqualTo("alice");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsWhenEmailTaken() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register("alice", "alice@example.com", "password123", "Alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void register_throwsWhenUsernameTaken() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register("alice", "alice@example.com", "password123", "Alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already taken");
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_throwsForOAuthUser() {
        sampleUser.setProvider(User.AuthProvider.GITHUB);
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() ->
                authService.changePassword(1L, "old", "newpassword"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changePassword_throwsWhenCurrentPasswordWrong() {
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong", sampleUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.changePassword(1L, "wrong", "newpassword"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ── deactivate / reactivate ───────────────────────────────────────────────

    @Test
    void deactivateAccount_setsActiveFalse() {
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        authService.deactivateAccount(1L);

        assertThat(sampleUser.isActive()).isFalse();
        verify(userRepository).save(sampleUser);
    }

    @Test
    void getUserByEmail_returnsUser() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleUser));

        Optional<User> result = authService.getUserByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }
}