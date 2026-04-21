package com.authservice.codesync.service;

import com.authservice.codesync.entity.User;

import java.util.List;
import java.util.Optional;

public interface AuthService {

    // ── Authentication ─────────────────────────────────────────────────────

    /**
     * Register a new local (email/password) user.
     * @return the saved User entity
     */
    User register(String username, String email, String rawPassword, String fullName);

    /**
     * Authenticate with email + password; returns a signed JWT access token.
     */
    String login(String email, String rawPassword);

    /**
     * Validate a JWT token string.
     */
    boolean validateToken(String token);

    /**
     * Issue a new access token from a valid refresh token.
     */
    String refreshToken(String refreshToken);

    // ── Profile management ────────────────────────────────────────────────

    Optional<User> getUserByEmail(String email);

    Optional<User> getUserById(Long userId);

    User updateProfile(Long userId, String username, String fullName, String bio, String avatarUrl);

    void changePassword(Long userId, String currentPassword, String newPassword);

    // ── Discovery ────────────────────────────────────────────────────────

    List<User> searchUsers(String query);

    // ── Admin / lifecycle ─────────────────────────────────────────────────

    void deactivateAccount(Long userId);

    void reactivateAccount(Long userId);

    void deleteAccount(Long userId);

    List<User> getAllUsers();
}