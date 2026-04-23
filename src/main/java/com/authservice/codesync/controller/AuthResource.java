package com.authservice.codesync.controller;

import com.authservice.codesync.entity.User;
import com.authservice.codesync.security.JwtTokenProvider;
import com.authservice.codesync.service.AuthService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, logout, token ops, profile and admin endpoints")
public class AuthResource {

    private final AuthService           authService;
    private final JwtTokenProvider      jwtTokenProvider;
    private final TokenBlacklistService blacklistService;

    public AuthResource(AuthService authService,
                        JwtTokenProvider jwtTokenProvider,
                        TokenBlacklistService blacklistService) {
        this.authService      = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.blacklistService = blacklistService;
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Operation(summary = "Register a new user",
               description = "Creates a LOCAL account. Returns user data only — call POST /login to receive tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered — call /login to get tokens"),
        @ApiResponse(responseCode = "400", description = "Validation error or email/username already taken")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterBody body) {
        User user = authService.register(body.username, body.email, body.password, body.fullName);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Registration successful. Please login to obtain your access token.",
                "user",    toDto(user)
        ));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Login with email and password",
               description = "Authenticates user and returns JWT access + refresh tokens. "
                           + "Inactivity window starts from this point.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful — tokens returned"),
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

    // ── Logout ────────────────────────────────────────────────────────────────

    @Operation(summary = "Logout — invalidate access token immediately",
               security = @SecurityRequirement(name = "bearerAuth"),
               description = "Blacklists the current access token in Redis so it cannot be reused even if "
                           + "the client still holds it. Also clears the inactivity tracking window.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logged out successfully"),
        @ApiResponse(responseCode = "401", description = "No valid token provided")
    })
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                     @AuthenticationPrincipal UserDetails principal) {
        String token = extractToken(request);
        if (token != null && principal != null) {
            User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
            authService.logout(token, user.getUserId());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Operation(summary = "Refresh access token",
               description = "Exchange a valid refresh token for a new access token. "
                           + "Also slides the inactivity window forward.")
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

    @Operation(summary = "Validate a JWT token — checks signature, expiry AND blacklist")
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> body) {
        boolean valid = authService.validateToken(body.get("token"));
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    // ── Session status (inactivity remaining) ─────────────────────────────────

    @Operation(summary = "Session status — returns remaining inactivity time in ms",
               security = @SecurityRequirement(name = "bearerAuth"),
               description = "Useful for the Angular frontend to show a 'session about to expire' warning "
                           + "before the server-side inactivity TTL fires.")
    @GetMapping("/session/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> sessionStatus(@AuthenticationPrincipal UserDetails principal) {
        User user = authService.getUserByEmail(principal.getUsername()).orElseThrow();
        long remainingMs = blacklistService.getRemainingActivityMs(user.getUserId());
        return ResponseEntity.ok(Map.of(
                "active",         remainingMs > 0,
                "remainingMs",    remainingMs,
                "remainingMins",  remainingMs / 60000
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

    // ── Search ────────────────────────────────────────────────────────────────

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
