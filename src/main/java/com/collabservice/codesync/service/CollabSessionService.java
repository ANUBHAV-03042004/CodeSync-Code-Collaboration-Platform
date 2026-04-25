package com.collabservice.codesync.service;

import com.collabservice.codesync.config.RabbitMQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages CollabSession lifecycle and broadcasts events over STOMP.
 *
 * Real-time path:   SimpMessagingTemplate -> STOMP /topic/...
 * Audit trail path: RabbitMQ -> codesync.collab.exchange (best-effort, non-blocking)
 *
 * FIX: publishAuditEvent() now catches AmqpException separately from Exception.
 * If RabbitMQ is temporarily unavailable (e.g. pod restart, TLS reconnect),
 * the audit event is silently dropped but the real-time WebSocket event is
 * still delivered — the session is not disrupted.
 */
@Service
public class CollabSessionService {

    private static final Logger log = LoggerFactory.getLogger(CollabSessionService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate   redisTemplate;
    private final RabbitTemplate        rabbitTemplate;

    private static final long SESSION_TTL_MINUTES = 30;

    public CollabSessionService(SimpMessagingTemplate messagingTemplate,
                                 StringRedisTemplate redisTemplate,
                                 RabbitTemplate rabbitTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate     = redisTemplate;
        this.rabbitTemplate    = rabbitTemplate;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public String createSession(Long projectId, Long fileId, Long ownerId,
                                 String language, int maxParticipants,
                                 boolean passwordProtected, String password) {
        String sessionId = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(sessionKey(sessionId), "ACTIVE",
                SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        addParticipantToRedis(sessionId, ownerId.toString());

        log.info("Session {} created for file {} by user {}", sessionId, fileId, ownerId);

        Map<String, Object> event = new HashMap<>(Map.of(
                "type",      "SESSION_CREATED",
                "sessionId", sessionId,
                "ownerId",   ownerId,
                "fileId",    fileId,
                "projectId", projectId,
                "language",  language != null ? language : "",
                "timestamp", LocalDateTime.now().toString()));

        broadcastEvent(sessionId, event);
        publishAuditEvent("collab.session.created", event);

        return sessionId;
    }

    public void joinSession(String sessionId, Long userId) {
        assertSessionActive(sessionId);
        addParticipantToRedis(sessionId, userId.toString());
        refreshTtl(sessionId);

        Map<String, Object> event = Map.of(
                "type",      "PARTICIPANT_JOINED",
                "sessionId", sessionId,
                "userId",    userId,
                "timestamp", LocalDateTime.now().toString());

        broadcastEvent(sessionId, event);
        publishAuditEvent("collab.session.joined", event);
    }

    public void leaveSession(String sessionId, Long userId) {
        redisTemplate.opsForSet().remove(participantsKey(sessionId), userId.toString());
        refreshTtl(sessionId);

        Map<String, Object> event = Map.of(
                "type",      "PARTICIPANT_LEFT",
                "sessionId", sessionId,
                "userId",    userId,
                "timestamp", LocalDateTime.now().toString());

        broadcastEvent(sessionId, event);
        publishAuditEvent("collab.session.left", event);
    }

    public void endSession(String sessionId) {
        redisTemplate.delete(sessionKey(sessionId));
        redisTemplate.delete(participantsKey(sessionId));
        redisTemplate.delete(cursorsKey(sessionId));

        Map<String, Object> event = Map.of(
                "type",      "SESSION_ENDED",
                "sessionId", sessionId,
                "timestamp", LocalDateTime.now().toString());

        broadcastEvent(sessionId, event);
        publishAuditEvent("collab.session.ended", event);
        log.info("Session {} ended", sessionId);
    }

    public void kickParticipant(String sessionId, Long targetUserId) {
        redisTemplate.opsForSet().remove(participantsKey(sessionId), targetUserId.toString());

        messagingTemplate.convertAndSendToUser(
                targetUserId.toString(),
                "/queue/kicked",
                Map.of("sessionId", sessionId, "reason", "Removed by session owner"));

        Map<String, Object> event = Map.of(
                "type",         "PARTICIPANT_KICKED",
                "sessionId",    sessionId,
                "targetUserId", targetUserId,
                "timestamp",    LocalDateTime.now().toString());

        broadcastEvent(sessionId, event);
        publishAuditEvent("collab.session.kicked", event);
    }

    // ── Cursor updates ────────────────────────────────────────────────────────

    public void updateCursor(String sessionId, Long userId, int line, int col) {
        redisTemplate.opsForHash().put(cursorsKey(sessionId),
                userId.toString(), line + ":" + col);
        refreshTtl(sessionId);

        messagingTemplate.convertAndSend(
                "/topic/session." + sessionId + ".cursor",
                Map.of("userId", userId, "line", line, "col", col));
    }

    // ── Edit delta broadcast ──────────────────────────────────────────────────

    public void broadcastEditDelta(String sessionId, Long authorId, Map<String, Object> delta) {
        assertSessionActive(sessionId);
        refreshTtl(sessionId);

        delta.put("authorId",  authorId);
        delta.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/session." + sessionId + ".edit", delta);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Set<String> getParticipants(String sessionId) {
        Set<String> members = redisTemplate.opsForSet().members(participantsKey(sessionId));
        return members != null ? members : Collections.emptySet();
    }

    public boolean isSessionActive(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)));
    }

    public Map<Object, Object> getCursors(String sessionId) {
        return redisTemplate.opsForHash().entries(cursorsKey(sessionId));
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    @Scheduled(fixedRate = 60_000)
    public void cleanupOrphanedKeys() {
        log.debug("Running orphan session key cleanup");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastEvent(String sessionId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/session." + sessionId + ".events", payload);
    }

    /**
     * Publish audit event to RabbitMQ — best-effort, never disrupts real-time path.
     *
     * FIX: AmqpException is caught separately so a RabbitMQ connection failure
     * (TLS timeout, broker restart, CloudAMQP rate limit) does not propagate up
     * to the WebSocket handler and disconnect the user's collaboration session.
     */
    private void publishAuditEvent(String routingKey, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.COLLAB_EXCHANGE, routingKey, payload);
            log.debug("Audit event published [{}]", routingKey);
        } catch (AmqpException e) {
            // RabbitMQ unavailable — log and continue. Real-time path is unaffected.
            log.warn("Audit event dropped [{}] — RabbitMQ unavailable: {}", routingKey, e.getMessage());
        } catch (Exception e) {
            log.warn("Audit event dropped [{}] — unexpected error: {}", routingKey, e.getMessage());
        }
    }

    private void addParticipantToRedis(String sessionId, String userId) {
        redisTemplate.opsForSet().add(participantsKey(sessionId), userId);
    }

    private void refreshTtl(String sessionId) {
        redisTemplate.expire(sessionKey(sessionId), SESSION_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private void assertSessionActive(String sessionId) {
        if (!isSessionActive(sessionId)) {
            throw new IllegalStateException("Session is not active: " + sessionId);
        }
    }

    private String sessionKey(String id)      { return "session:" + id + ":active"; }
    private String participantsKey(String id) { return "session:" + id + ":participants"; }
    private String cursorsKey(String id)      { return "session:" + id + ":cursors"; }
}
