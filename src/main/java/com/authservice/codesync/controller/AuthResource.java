package com.authservice.codesync.controller;

import com.authservice.codesync.entity.User;
import com.authservice.codesync.security.JwtTokenProvider;
import com.authservice.codesync.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token operations, profile management and admin endpoints")
public class AuthResource {

    private final AuthService      authService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResource(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService      = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Operation(summary = "Register a new user",
               description = "Creates a new LOCAL provider account. Auto-logs in and returns JWT access token + refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered and logged in — tokens returned"),
        @ApiResponse(responseCode = "400", description = "Validation error or email/username already taken",
                     content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterBody body) {
        // 1. Create the account
        User user = authService.register(body.username, body.email, body.password, body.fullName);

        // 2. Auto-login: issue tokens immediately so the client needs no second round-trip
        org.springframework.security.core.userdetails.UserDetails ud =
            org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password("")
                .roles(user.getRole().name())
                .build();

        String accessToken  = jwtTokenProvider.generateAccessToken(ud, user.getUserId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(ud);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "tokenType",    "Bearer",
                "user",         toDto(user)
        ));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Login with email and password",
               description = "Authenticates a user and returns a JWT access token + refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful, tokens returned"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginBody body) {
        String accessToken = authService.login(body.email, body.password);

        User user = authService.getUserByEmail(body.email).orElseThrow();
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail()).password("").roles(user.getRole().name()).build());

        return ResponseEntity.ok(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "tokenType",    "Bearer",
                "user",         toDto(user)
        ));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Operation(summary = "Refresh access token",
               description = "Exchange a valid refresh token for a new access token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New access token issued"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String newToken = authService.refreshToken(body.get("refreshToken"));
        return ResponseEntity.ok(Map.of("accessToken", newToken, "tokenType", "Bearer"));
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    @Operation(summary = "Validate a JWT token",
               description = "Used by other microservices / API gateway to verify token validity.")
    @ApiResponse(responseCode = "200", description = "Returns {valid: true/false}")
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> body) {
        boolean valid = authService.validateToken(body.get("token"));
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorised")
    })
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails principal) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        return ResponseEntity.ok(toDto(user));
    }

    @Operation(summary = "Update current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetails principal,
                                           @RequestBody UpdateProfileBody body) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        User updated = authService.updateProfile(
                user.getUserId(), body.username, body.fullName, body.bio, body.avatarUrl);
        return ResponseEntity.ok(toDto(updated));
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @Operation(summary = "Change password", security = @SecurityRequirement(name = "bearerAuth"),
               description = "Only available for LOCAL accounts (not OAuth2 users).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password changed"),
        @ApiResponse(responseCode = "400", description = "Current password incorrect or OAuth account")
    })
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails principal,
                                             @RequestBody @Valid ChangePasswordBody body) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        authService.changePassword(user.getUserId(), body.currentPassword, body.newPassword);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    // ── Search & lookup ───────────────────────────────────────────────────────

    @Operation(summary = "Search users by username prefix")
    @ApiResponse(responseCode = "200", description = "Matching users returned")
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @Parameter(description = "Search query (username prefix)", example = "alice")
            @RequestParam("q") String query) {
        List<?> results = authService.searchUsers(query)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Get user by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        return authService.getUserById(userId)
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Account lifecycle ─────────────────────────────────────────────────────

    @Operation(summary = "Deactivate own account", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Account deactivated")
    @PostMapping("/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deactivate(@AuthenticationPrincipal UserDetails principal) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        authService.deactivateAccount(user.getUserId());
        return ResponseEntity.ok(Map.of("message", "Account deactivated"));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @Operation(summary = "[ADMIN] List all users", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "All users returned")
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers().stream().map(this::toDto).toList());
    }

    @Operation(summary = "[ADMIN] Reactivate a user account", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Account reactivated")
    @PutMapping("/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reactivate(@PathVariable Long userId) {
        authService.reactivateAccount(userId);
        return ResponseEntity.ok(Map.of("message", "Account reactivated"));
    }

    @Operation(summary = "[ADMIN] Permanently delete a user", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted"),
        @ApiResponse(responseCode = "403", description = "Forbidden — admin role required")
    })
    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        authService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Request body records ──────────────────────────────────────────────────

    record RegisterBody(
            @NotBlank @Size(min = 3, max = 30) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            String fullName) {}

    record LoginBody(@NotBlank String email, @NotBlank String password) {}

    record UpdateProfileBody(String username, String fullName, String bio, String avatarUrl) {}

    record ChangePasswordBody(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8) String newPassword) {}

    // ── Mapper ────────────────────────────────────────────────────────────────

    private Map<String, Object> toDto(User user) {
        return Map.of(
                "userId",    user.getUserId(),
                "username",  user.getUsername(),
                "email",     user.getEmail(),
                "fullName",  user.getFullName()  != null ? user.getFullName()  : "",
                "role",      user.getRole().name(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "bio",       user.getBio()       != null ? user.getBio()       : "",
                "provider",  user.getProvider().name(),
                "isActive",  user.isActive(),
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
        );
    }
}
