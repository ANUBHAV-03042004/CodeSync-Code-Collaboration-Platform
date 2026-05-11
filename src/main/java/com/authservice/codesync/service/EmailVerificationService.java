package com.authservice.codesync.service;

public interface EmailVerificationService {

    /**
     * Generate a verification token, store it, and send the verification email.
     * Called immediately after LOCAL user registration.
     *
     * @param userId the newly registered user's ID
     */
    void sendVerificationEmail(Long userId);

    /**
     * Validate the token and activate the user's account.
     *
     * @param token the raw token from the email link
     * @throws IllegalArgumentException if the token is invalid, expired, or already used
     */
    void verifyEmail(String token);
}
