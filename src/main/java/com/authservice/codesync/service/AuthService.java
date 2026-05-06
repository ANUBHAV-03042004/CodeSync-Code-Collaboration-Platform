package com.authservice.codesync.service;

import com.authservice.codesync.entity.User;

import java.util.List;
import java.util.Optional;

public interface AuthService {

    // ── Authentication ─────────────────────────────────────────────────────

    User register(String username, String email, String rawPassword, String fullName);

    String login(String email, String rawPassword);

    String generateRefreshToken(User user);

    /**
     * Blacklist both the access token and the refresh token, and clear the inactivity window.
     * Must be called on explicit user logout.
     *
     * @param accessToken  the raw Bearer JWT string
     * @param refreshToken the refresh token to also invalidate (may be null)
     * @param userId       the authenticated user's ID
     */
    void logout(String accessToken, String refreshToken, Long userId);

    boolean validateToken(String token);

    String refreshToken(String refreshToken);

    // ── Profile management ────────────────────────────────────────────────

    Optional<User> getUserByEmail(String email);

    Optional<User> getUserById(Long userId);

    User updateProfile(Long userId, String username, String fullName,
                       String bio, String avatarUrl);

    void changePassword(Long userId, String currentPassword, String newPassword);

    // ── Discovery ────────────────────────────────────────────────────────

    List<User> searchUsers(String query);

    // ── Admin / lifecycle ─────────────────────────────────────────────────

    void deactivateAccount(Long userId);

    void reactivateAccount(Long userId);

    void deleteAccount(Long userId);

    List<User> getAllUsers();
}