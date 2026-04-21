package com.authservice.codesync.controller;

import com.authservice.codesync.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Password Reset", description = "Forgot password and reset password endpoints")
public class PasswordResetResource {

    private final PasswordResetService passwordResetService;

    public PasswordResetResource(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    @Operation(
        summary = "Request a password reset link",
        description = """
            Sends a password-reset email to the given address if it belongs to an active LOCAL account.
            Always returns 200 OK to avoid leaking whether the email is registered.
            The reset link is valid for 15 minutes.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "If the email exists, a reset link has been sent")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequest body) {

        passwordResetService.initiateForgotPassword(body.email());

        // Intentionally vague – do not reveal whether the email exists
        return ResponseEntity.ok(Map.of(
            "message", "If that email is registered, a password reset link has been sent."
        ));
    }

    // ── Validate Token ────────────────────────────────────────────────────────

    @Operation(
        summary = "Validate a password-reset token",
        description = "Returns whether the given token is valid and not yet expired or used. " +
                      "Call this before rendering the reset-password form."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token validity returned")
    })
    @GetMapping("/reset-password/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestParam("token") @NotBlank String token) {

        boolean valid = passwordResetService.validateResetToken(token);
        return ResponseEntity.ok(Map.of(
            "valid",   valid,
            "message", valid ? "Token is valid" : "Token is invalid or has expired"
        ));
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Operation(
        summary = "Reset password using a valid token",
        description = """
            Consumes the one-time token and sets a new password.
            The token must be valid (not expired, not already used).
            After a successful reset the token is invalidated.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid, expired or already-used token")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestBody @Valid ResetPasswordRequest body) {

        passwordResetService.resetPassword(body.token(), body.newPassword());
        return ResponseEntity.ok(Map.of(
            "message", "Password has been reset successfully. You may now log in."
        ));
    }

    // ── Request body records ──────────────────────────────────────────────────

    record ForgotPasswordRequest(
            @NotBlank @Email String email) {}

    record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, message = "New password must be at least 8 characters") String newPassword) {}
}
