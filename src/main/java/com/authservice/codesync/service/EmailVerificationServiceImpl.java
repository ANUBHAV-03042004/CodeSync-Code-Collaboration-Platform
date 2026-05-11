package com.authservice.codesync.service;

import com.authservice.codesync.entity.EmailVerificationToken;
import com.authservice.codesync.entity.User;
import com.authservice.codesync.repository.EmailVerificationTokenRepository;
import com.authservice.codesync.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@Transactional
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationServiceImpl.class);
    private static final int TOKEN_EXPIRY_HOURS = 24;
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository                   userRepo;
    private final EmailService                     emailService;

    @Value("${app.frontend-url:https://yourscode.netlify.app}")
    private String frontendUrl;

    public EmailVerificationServiceImpl(EmailVerificationTokenRepository tokenRepo,
                                        UserRepository userRepo,
                                        EmailService emailService) {
        this.tokenRepo    = tokenRepo;
        this.userRepo     = userRepo;
        this.emailService = emailService;
    }

    @Override
    public void sendVerificationEmail(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Invalidate any previous unused tokens before issuing a fresh one
        tokenRepo.invalidateAllForUser(user);

        String rawToken  = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);

        tokenRepo.save(new EmailVerificationToken(rawToken, user, expiresAt));

        // Link goes to the FRONTEND, which calls back to the API.
        // Pattern: https://yourscode.netlify.app/verify-email?token=<rawToken>
        // The Angular /verify-email page reads the token from the URL and POSTs to
        // POST /api/v1/auth/verify-email  { "token": "<rawToken>" }
        String verifyLink = frontendUrl + "/verify-email?token=" + rawToken;
        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verifyLink);

        log.info("Verification email sent to {} (userId={})", user.getEmail(), userId);
    }

    @Override
    public void verifyEmail(String token) {
        EmailVerificationToken evt = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link."));

        if (!evt.isValid()) {
            throw new IllegalArgumentException(
                    evt.isUsed() ? "Email already verified." : "Verification link has expired. Request a new one.");
        }

        User user = evt.getUser();
        user.setEmailVerified(true);
        user.setActive(true);
        userRepo.save(user);

        evt.setUsed(true);
        tokenRepo.save(evt);

        log.info("Email verified for user: {} (userId={})", user.getEmail(), user.getUserId());
    }

    @Scheduled(fixedRate = 3_600_000)
    public void purgeExpiredTokens() {
        tokenRepo.deleteExpiredAndUsed(LocalDateTime.now());
        log.debug("Purged expired/used email verification tokens");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
