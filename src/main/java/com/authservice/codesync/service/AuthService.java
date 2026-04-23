package com.authservice.codesync.service;

import com.authservice.codesync.entity.User;

import java.util.List;
import java.util.Optional;

public interface AuthService {

    // ── Authentication ─────────────────────────────────────────────────────

    User register(String username, String email, String rawPassword, String fullName);

    String login(String email, String rawPassword);

    /**
     * Blacklist the access token and clear the inactivity window.
     * Must be called on explicit user logout.
     *
     * @param token  the raw Bearer JWT string
     * @param userId the authenticated user's ID
     */
    void logout(String token, Long userId);

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
