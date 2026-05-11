package com.authservice.codesync.controller;

import com.authservice.codesync.entity.User;
import com.authservice.codesync.security.JwtTokenProvider;
import com.authservice.codesync.service.AuthService;
import com.authservice.codesync.service.EmailVerificationService;
import com.authservice.codesync.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, logout, token ops, profile and admin endpoints")
public class AuthResource {

    @Value("${app.frontend-url:https://yourscode.netlify.app}")
    private String frontendUrl;

    private final AuthService               authService;
    private final JwtTokenProvider          jwtTokenProvider;
    private final TokenBlacklistService     blacklistService;
    private final EmailVerificationService  emailVerificationService;

    public AuthResource(AuthService authService,
                        JwtTokenProvider jwtTokenProvider,
                        TokenBlacklistService blacklistService,
                        EmailVerificationService emailVerificationService) {
        this.authService              = authService;
        this.jwtTokenProvider         = jwtTokenProvider;
        this.blacklistService         = blacklistService;
        this.emailVerificationService = emailVerificationService;
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Operation(summary = "Register a new user",
               description = "Creates a LOCAL account and sends a verification email to the provided address. "
                           + "The account is inactive until the email is verified.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered — verification email sent"),
        @ApiResponse(responseCode = "400", description = "Validation error or email/username already taken")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterBody body) {
        User user = authService.register(body.username, body.email, body.password, body.fullName);

        // Verification email is already sent by authService.register()
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Registration successful! Please check your email to verify your account.",
                "user",    toDto(user)
        ));
    }

    // ── Email verification ────────────────────────────────────────────────────

    @Operation(summary = "Verify email address",
               description = "Validates the token from the verification email and activates the account.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified — account activated"),
        @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used token")
    })
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token is required."));
        }
        try {
            emailVerificationService.verifyEmail(token);
            return ResponseEntity.ok(Map.of(
                    "message", "Email verified successfully. You can now sign in."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Redirect email verification link to the frontend",
               description = "Handles browser clicks on verification emails (GET). "
                           + "Verifies the token and redirects to the frontend login page.")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirect to frontend login page"),
        @ApiResponse(responseCode = "400", description = "Token parameter is missing or invalid")
    })
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmailRedirect(@RequestParam(required = false) String token) {
        if (token == null || token.isBlank()) {
            URI location = URI.create(frontendUrl + "/login?error=missing_token");
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
        }
        
        try {
            authService.verifyEmail(token);
            URI location = URI.create(frontendUrl + "/login?verified=true");
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
        } catch (Exception e) {
            URI location = URI.create(frontendUrl + "/login?error=" + e.getMessage().replaceAll(" ", "_"));
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
        }
    }

    @Operation(summary = "Resend verification email",
               description = "Issues a fresh token and resends the verification email. "
                           + "Use when the original link has expired.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verification email resent (if account exists and is unverified)"),
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        }
        // Look up user — always return 200 to avoid email enumeration
        authService.getUserByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified() && user.getProvider() == User.AuthProvider.LOCAL) {
                emailVerificationService.sendVerificationEmail(user.getUserId());
            }
        });
        return ResponseEntity.ok(Map.of(
                "message", "If an unverified account exists for that address, a new link has been sent."
        ));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Login with email and password",
               description = "Authenticates user and returns JWT access + refresh tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful — tokens returned"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "403", description = "Email not verified yet")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginBody body) {
        // Guard: reject unverified LOCAL users before authentication
        User candidate = authService.getUserByEmail(body.email).orElse(null);
        if (candidate != null
                && candidate.getProvider() == User.AuthProvider.LOCAL
                && !candidate.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Please verify your email address before signing in. "
                           + "Check your inbox or use /resend-verification."
            ));
        }

        String accessToken  = authService.login(body.email, body.password);
        User user           = authService.getUserByEmail(body.email).orElseThrow();
        String refreshToken = authService.generateRefreshToken(user);

        return ResponseEntity.ok(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "tokenType",    "Bearer",
                "user",         toDto(user)
        ));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Operation(summary = "Logout — invalidate access AND refresh tokens immediately",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                     @AuthenticationPrincipal UserDetails principal,
                                     @RequestBody(required = false) Map<String, String> body) {
        String accessToken  = extractToken(request);
        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (accessToken != null && principal != null) {
            User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
            authService.logout(accessToken, refreshToken, user.getUserId());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String newToken = authService.refreshToken(body.get("refreshToken"));
        return ResponseEntity.ok(Map.of("accessToken", newToken, "tokenType", "Bearer"));
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> body) {
        boolean valid = authService.validateToken(body.get("token"));
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    // ── Session status ────────────────────────────────────────────────────────

    @GetMapping("/session/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> sessionStatus(@AuthenticationPrincipal UserDetails principal) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        long remainingMs = blacklistService.getRemainingActivityMs(user.getUserId());
        return ResponseEntity.ok(Map.of(
                "active",        remainingMs > 0,
                "remainingMs",   remainingMs,
                "remainingMins", remainingMs / 60000
        ));
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails principal) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        return ResponseEntity.ok(toDto(user));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetails principal,
                                           @RequestBody UpdateProfileBody body) {
        User user    = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        User updated = authService.updateProfile(
                user.getUserId(), body.username, body.fullName, body.bio, body.avatarUrl);
        return ResponseEntity.ok(toDto(updated));
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails principal,
                                             @RequestBody @Valid ChangePasswordBody body) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        authService.changePassword(user.getUserId(), body.currentPassword, body.newPassword);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    // ── Search / User lookup ──────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam("q") String query) {
        List<?> results = authService.searchUsers(query)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        return authService.getUserById(userId)
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Account lifecycle ─────────────────────────────────────────────────────

    @PostMapping("/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deactivate(@AuthenticationPrincipal UserDetails principal) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        authService.deactivateAccount(user.getUserId());
        return ResponseEntity.ok(Map.of("message", "Account deactivated"));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers().stream().map(this::toDto).toList());
    }

    @PutMapping("/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reactivate(@PathVariable Long userId) {
        authService.reactivateAccount(userId);
        return ResponseEntity.ok(Map.of("message", "Account reactivated"));
    }

    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        authService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Request body records ──────────────────────────────────────────────────

    public record RegisterBody(
            @NotBlank @Size(min = 3, max = 30) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            String fullName) {}

    public record LoginBody(@NotBlank String email, @NotBlank String password) {}

    record UpdateProfileBody(String username, String fullName, String bio, String avatarUrl) {}

    record ChangePasswordBody(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8) String newPassword) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (StringUtils.hasText(bearer) && bearer.startsWith("Bearer "))
                ? bearer.substring(7) : null;
    }

    private Map<String, Object> toDto(User user) {
        // Map.of() is limited to 10 pairs — use LinkedHashMap for 11+ fields
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("userId",        user.getUserId());
        dto.put("username",      user.getUsername());
        dto.put("email",         user.getEmail());
        dto.put("fullName",      user.getFullName()  != null ? user.getFullName()  : "");
        dto.put("role",          user.getRole().name());
        dto.put("avatarUrl",     user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        dto.put("bio",           user.getBio()       != null ? user.getBio()       : "");
        dto.put("provider",      user.getProvider().name());
        dto.put("isActive",      user.isActive());
        dto.put("emailVerified", user.isEmailVerified());
        dto.put("createdAt",     user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
        return dto;
    }
}
