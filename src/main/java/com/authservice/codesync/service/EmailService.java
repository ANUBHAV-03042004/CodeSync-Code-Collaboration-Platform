package com.authservice.codesync.service;

public interface EmailService {

    /**
     * Send a password-reset email containing the reset link.
     *
     * @param toEmail   recipient address
     * @param resetLink full URL the user must click (includes the token)
     */
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
