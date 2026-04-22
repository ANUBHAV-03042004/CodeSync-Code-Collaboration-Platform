package com.authservice.codesync;

import com.authservice.codesync.controller.AuthResource;
import com.authservice.codesync.entity.User;
import com.authservice.codesync.repository.UserRepository;
import com.authservice.codesync.security.JwtTokenProvider;
import com.authservice.codesync.service.AuthService;
import com.authservice.codesync.service.AuthServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    // ── Service-layer mocks ───────────────────────────────────────────────────
    @Mock UserRepository        userRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock JwtTokenProvider      jwtTokenProvider;
    @Mock AuthenticationManager authenticationManager;
    @InjectMocks AuthServiceImpl authService;

    // ── Controller-layer mocks ────────────────────────────────────────────────
    @Mock AuthService      authServiceMock;
    @Mock JwtTokenProvider jwtTokenProviderMock;
    private AuthResource   authResource;

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

        authResource = new AuthResource(authServiceMock, jwtTokenProviderMock);
    }

    // =========================================================================
    // AuthServiceImpl — register
    // =========================================================================

    @Nested
    @DisplayName("AuthServiceImpl — register")
    class RegisterServiceTests {

        @Test
        @DisplayName("register success — saves user, never calls JwtTokenProvider")
        void register_success_noTokenGenerated() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);

            User result = authService.register("alice", "alice@example.com", "password123", "Alice");

            assertThat(result.getUsername()).isEqualTo("alice");
            verify(userRepository).save(any(User.class));
            // Service must NEVER generate tokens during registration
            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("register throws when email already in use")
        void register_throwsWhenEmailTaken() {
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() ->
                    authService.register("alice", "alice@example.com", "password123", "Alice"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already in use");
        }

        @Test
        @DisplayName("register throws when username already taken")
        void register_throwsWhenUsernameTaken() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() ->
                    authService.register("alice", "alice@example.com", "password123", "Alice"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username already taken");
        }
    }

    // =========================================================================
    // POST /register — controller must return NO tokens
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/register — no tokens in response")
    class RegisterEndpointTests {

        @Test
        @DisplayName("register returns 201 with user data but NO accessToken or refreshToken")
        @SuppressWarnings("unchecked")
        void register_returns201_noTokens() {
            when(authServiceMock.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(sampleUser);

            AuthResource.RegisterBody body =
                    new AuthResource.RegisterBody("alice", "alice@example.com", "password123", "Alice");

            ResponseEntity<?> response = authResource.register(body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            Map<String, Object> body2 = (Map<String, Object>) response.getBody();
            assertThat(body2).isNotNull();
            assertThat(body2).containsKey("user");
            assertThat(body2).containsKey("message");

            // These keys must be absent — tokens only come from /login
            assertThat(body2).doesNotContainKey("accessToken");
            assertThat(body2).doesNotContainKey("refreshToken");
            assertThat(body2).doesNotContainKey("tokenType");

            verifyNoInteractions(jwtTokenProviderMock);
        }

        @Test
        @DisplayName("register response message directs user to login")
        @SuppressWarnings("unchecked")
        void register_messageDirectsToLogin() {
            when(authServiceMock.register(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(sampleUser);

            AuthResource.RegisterBody body =
                    new AuthResource.RegisterBody("alice", "alice@example.com", "password123", "Alice");

            ResponseEntity<?> response = authResource.register(body);
            Map<String, Object> body2 = (Map<String, Object>) response.getBody();

            assertThat(body2).isNotNull();
            assertThat(body2.get("message").toString()).containsIgnoringCase("login");
        }
    }

    // =========================================================================
    // POST /login — controller MUST return tokens
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/login — tokens present in response")
    class LoginEndpointTests {

        @Test
        @DisplayName("login returns 200 with accessToken AND refreshToken")
        @SuppressWarnings("unchecked")
        void login_returns200WithBothTokens() {
            when(authServiceMock.login("alice@example.com", "password123"))
                    .thenReturn("mock-access-token");
            when(authServiceMock.getUserByEmail("alice@example.com"))
                    .thenReturn(Optional.of(sampleUser));
            when(jwtTokenProviderMock.generateRefreshToken(any()))
                    .thenReturn("mock-refresh-token");

            AuthResource.LoginBody body =
                    new AuthResource.LoginBody("alice@example.com", "password123");

            ResponseEntity<?> response = authResource.login(body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> body2 = (Map<String, Object>) response.getBody();
            assertThat(body2).isNotNull();
            assertThat(body2).containsKey("accessToken");
            assertThat(body2).containsKey("refreshToken");
            assertThat(body2).containsKey("tokenType");
            assertThat(body2).containsKey("user");
            assertThat(body2.get("accessToken")).isEqualTo("mock-access-token");
            assertThat(body2.get("refreshToken")).isEqualTo("mock-refresh-token");
            assertThat(body2.get("tokenType")).isEqualTo("Bearer");
        }
    }

    // =========================================================================
    // AuthServiceImpl — other tests
    // =========================================================================

    @Nested
    @DisplayName("AuthServiceImpl — changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("throws for OAuth user")
        void changePassword_throwsForOAuthUser() {
            sampleUser.setProvider(User.AuthProvider.GITHUB);
            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> authService.changePassword(1L, "old", "newpassword"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("throws when current password is wrong")
        void changePassword_throwsWhenCurrentPasswordWrong() {
            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(sampleUser));
            when(passwordEncoder.matches("wrong", sampleUser.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(1L, "wrong", "newpassword"))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("AuthServiceImpl — account lifecycle")
    class AccountLifecycleTests {

        @Test
        @DisplayName("deactivateAccount sets isActive to false")
        void deactivateAccount_setsActiveFalse() {
            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);

            authService.deactivateAccount(1L);

            assertThat(sampleUser.isActive()).isFalse();
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("getUserByEmail returns the correct user")
        void getUserByEmail_returnsUser() {
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleUser));

            Optional<User> result = authService.getUserByEmail("alice@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("alice");
        }
    }
}