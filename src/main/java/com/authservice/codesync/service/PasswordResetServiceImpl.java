package com.authservice.codesync.service;

import com.authservice.codesync.entity.PasswordResetToken;
import com.authservice.codesync.entity.User;
import com.authservice.codesync.repository.PasswordResetTokenRepository;
import com.authservice.codesync.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@Transactional
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final int TOKEN_EXPIRY_MINUTES = 15;
    private static final int TOKEN_BYTES = 32; // 256-bit token → 43 URL-safe Base64 chars

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository               userRepository;
    private final EmailService                 emailService;
    private final PasswordEncoder              passwordEncoder;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public PasswordResetServiceImpl(PasswordResetTokenRepository tokenRepository,
                                    UserRepository               userRepository,
                                    EmailService                 emailService,
                                    PasswordEncoder              passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository  = userRepository;
        this.emailService    = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Initiate forgot-password ───────────────────────────────────────────────

    @Override
    public void initiateForgotPassword(String email) {
        Optional<User> optUser = userRepository.findByEmail(email);

        // Always log and return – do not expose whether email exists
        if (optUser.isEmpty()) {
            log.warn("Password reset requested for unknown email: {}", email);
            return;
        }

        User user = optUser.get();

        if (!user.isActive()) {
            log.warn("Password reset requested for inactive user: {}", email);
            return;
        }

        if (user.getProvider() != User.AuthProvider.LOCAL) {
            log.warn("Password reset requested for OAuth user: {} ({})", email, user.getProvider());
            return; // OAuth users cannot reset passwords here
        }

        // Invalidate any previous unused tokens
        tokenRepository.invalidateAllForUser(user);

        // Generate cryptographically secure token
        String rawToken = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES);

        PasswordResetToken resetToken = new PasswordResetToken(rawToken, user, expiresAt);
        tokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(email, resetLink);

        log.info("Password reset token issued for user: {}", email);
    }

    // ── Validate token ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        return tokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Override
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException(
                    resetToken.isUsed() ? "Password reset token has already been used"
                                        : "Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as consumed
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    /** Purge expired / used tokens every hour to keep the table clean. */
    @Scheduled(fixedRate = 3_600_000)
    public void purgeExpiredTokens() {
        tokenRepository.deleteExpiredAndUsed(LocalDateTime.now());
        log.debug("Purged expired/used password reset tokens");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
