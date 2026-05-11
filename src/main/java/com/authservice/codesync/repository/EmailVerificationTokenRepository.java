package com.authservice.codesync.repository;

import com.authservice.codesync.entity.EmailVerificationToken;
import com.authservice.codesync.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    /** Invalidate (mark used) all pending tokens for a user before issuing a new one. */
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(User user);

    /** Scheduled cleanup — remove tokens that are expired or already consumed. */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.used = true OR t.expiresAt < :now")
    void deleteExpiredAndUsed(LocalDateTime now);
}
