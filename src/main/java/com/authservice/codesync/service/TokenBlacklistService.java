package com.authservice.codesync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Manages two Redis key spaces:
 *
 * 1. Token blacklist  — "blacklist:{token}"
 *    Set when a user explicitly logs out. The key TTL matches the token's
 *    remaining validity so Redis self-cleans. Any blacklisted token is
 *    rejected immediately by JwtAuthenticationFilter regardless of its
 *    cryptographic validity.
 *
 * 2. Activity tracking — "activity:{userId}"
 *    Reset on every authenticated request. TTL = inactivity timeout (default 30 min).
 *    When the key expires the user is considered inactive; the next request
 *    returns 401 and the frontend redirects to /login.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String BLACKLIST_PREFIX  = "blacklist:";
    private static final String ACTIVITY_PREFIX   = "activity:";
    private static final String SEEN_PREFIX        = "seen:";       // permanent marker set on first login
    private static final String BLACKLISTED_VALUE  = "1";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.inactivity-timeout-ms:1800000}")
    private long inactivityTimeoutMs;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Blacklist (logout) ────────────────────────────────────────────────────

    /**
     * Blacklist a token for its remaining lifetime.
     * Call this on explicit logout so the token is dead server-side immediately.
     *
     * @param token         the raw JWT string
     * @param remainingMs   milliseconds until the token naturally expires
     */
    public void blacklist(String token, long remainingMs) {
        if (remainingMs <= 0) {
            // Token already expired — no need to blacklist
            return;
        }
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + token,
                BLACKLISTED_VALUE,
                remainingMs,
                TimeUnit.MILLISECONDS
        );
        log.debug("Token blacklisted for {}ms", remainingMs);
    }

    /**
     * Returns true if the token has been explicitly logged out.
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    // ── Activity tracking (inactivity auto-logout) ────────────────────────────

    /**
     * Record activity for a user — call this on every authenticated request.
     * Slides the inactivity window forward by resetting the TTL.
     * Also writes a permanent "seen" marker so the filter can distinguish
     * "never logged in" from "logged in but now idle".
     */
    public void recordActivity(Long userId) {
        // Sliding inactivity window (expires after inactivityTimeoutMs of silence)
        redisTemplate.opsForValue().set(
                ACTIVITY_PREFIX + userId,
                "active",
                inactivityTimeoutMs,
                TimeUnit.MILLISECONDS
        );
        // Permanent marker — no TTL — so we know this user has logged in before
        redisTemplate.opsForValue().setIfAbsent(SEEN_PREFIX + userId, "1");
    }

    /**
     * Returns true if this user has ever successfully logged in (activity was
     * recorded at least once). Used by the JWT filter to distinguish a fresh
     * token (no activity key yet — just issued) from a truly idle session.
     */
    public boolean hasActivityKeyEverBeenSet(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SEEN_PREFIX + userId));
    }

    /**
     * Returns true if the user has been active within the inactivity window.
     * Returns false when the Redis key has expired → user is considered inactive.
     */
    public boolean isUserActive(Long userId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(ACTIVITY_PREFIX + userId));
    }

    /**
     * Explicitly clear the activity key — call on logout so the inactivity
     * key does not linger until its TTL expires.
     * The seen-marker is also cleared so a fresh login re-seeds it correctly.
     */
    public void clearActivity(Long userId) {
        redisTemplate.delete(ACTIVITY_PREFIX + userId);
        redisTemplate.delete(SEEN_PREFIX + userId);
        log.debug("Activity cleared for user {}", userId);
    }

    /**
     * Returns remaining TTL in milliseconds for the activity key.
     * Useful for diagnostics / Swagger testing.
     */
    public long getRemainingActivityMs(Long userId) {
        Long ttl = redisTemplate.getExpire(ACTIVITY_PREFIX + userId, TimeUnit.MILLISECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
