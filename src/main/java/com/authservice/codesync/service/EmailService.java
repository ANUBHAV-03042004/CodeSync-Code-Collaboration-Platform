package com.authservice.codesync.service;

public interface EmailService {

    /**
     * Send a password-reset email containing the reset link.
     *
     * @param toEmail   recipient address
     * @param resetLink full URL the user must click (includes the token)
     */
    void sendPasswordResetEmail(String toEmail, String resetLink);

    /**
     * Send a post-registration verification email.
     * The link points to the FRONTEND (/verify-email?token=...) — never to the backend.
     *
     * @param toEmail    recipient address
     * @param username   display name for the greeting
     * @param verifyLink full frontend URL the user must click to activate their account
     */
    void sendVerificationEmail(String toEmail, String username, String verifyLink);
}
