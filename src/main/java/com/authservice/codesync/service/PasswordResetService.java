package com.authservice.codesync.service;

public interface PasswordResetService {

    /**
     * Initiate the forgot-password flow.
     * Generates a secure token, persists it, and sends the reset email.
     * Always returns void – never reveal whether the email exists.
     *
     * @param email the address entered by the user
     */
    void initiateForgotPassword(String email);

    /**
     * Validate a reset token without consuming it.
     *
     * @param token the raw token from the reset link
     * @return true if the token is valid and not expired
     */
    boolean validateResetToken(String token);

    /**
     * Consume a valid reset token and set a new password for the associated user.
     *
     * @param token       the raw token from the reset link
     * @param newPassword the new plain-text password (will be encoded)
     * @throws IllegalArgumentException if token is invalid, expired, or already used
     */
    void resetPassword(String token, String newPassword);
}
