package com.notificationservice.codesync.service;

import com.notificationservice.codesync.entity.Notification;
import com.notificationservice.codesync.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String BADGE_KEY_PREFIX = "notif:badge:";
    private static final long   BADGE_TTL_HOURS  = 24;

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate  messagingTemplate;
    private final StringRedisTemplate    redisTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                                SimpMessagingTemplate messagingTemplate,
                                StringRedisTemplate redisTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate      = messagingTemplate;
        this.redisTemplate          = redisTemplate;
    }

    public Notification send(Long recipientId, Long actorId,
                              Notification.NotificationType type,
                              String title, String message,
                              String relatedId, String relatedType,
                              String deepLinkUrl) {

        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .type(type)
                .title(title)
                .message(message)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .deepLinkUrl(deepLinkUrl)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        incrementBadge(recipientId);
        pushWebSocketNotification(recipientId, saved);
        log.info("Notification {} [{}] sent to user {}", saved.getNotificationId(), type, recipientId);
        return saved;
    }

    public void sendBulk(List<Long> recipientIds, Long actorId,
                          String title, String message, String deepLinkUrl) {
        recipientIds.forEach(recipientId ->
                send(recipientId, actorId, Notification.NotificationType.BROADCAST,
                        title, message, null, null, deepLinkUrl));
        log.info("Broadcast notification sent to {} users", recipientIds.size());
    }

    @Transactional(readOnly = true)
    public List<Notification> getByRecipient(Long recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnread(Long recipientId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long recipientId) {
        try {
            String cached = redisTemplate.opsForValue().get(BADGE_KEY_PREFIX + recipientId);
            if (cached != null) return Integer.parseInt(cached);
        } catch (Exception e) {
            log.warn("Redis read unavailable for badge count (user {}), falling back to DB: {}", recipientId, e.getMessage());
        }
        int count = notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
        try {
            redisTemplate.opsForValue().set(BADGE_KEY_PREFIX + recipientId,
                    String.valueOf(count), BADGE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis write unavailable, badge count not cached (user {}): {}", recipientId, e.getMessage());
        }
        return count;
    }

    public void markAsRead(Long notificationId, Long recipientId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.isRead() && n.getRecipientId().equals(recipientId)) {
                n.setRead(true);
                notificationRepository.save(n);
                decrementBadge(recipientId);
                pushBadgeCount(recipientId);
            }
        });
    }

    public void markAllRead(Long recipientId) {
        // DB update always runs — Redis failure must not roll this back
        int updated = notificationRepository.markAllReadByRecipient(recipientId);
        try {
            redisTemplate.opsForValue().set(BADGE_KEY_PREFIX + recipientId, "0",
                    BADGE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis unavailable, badge key not reset for user {}: {}", recipientId, e.getMessage());
        }
        if (updated > 0) pushBadgeCount(recipientId);
        log.info("Marked {} notifications as read for user {}", updated, recipientId);
    }

    public void deleteNotification(Long notificationId, Long recipientId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipientId().equals(recipientId)) {
                if (!n.isRead()) decrementBadge(recipientId);
                notificationRepository.deleteById(notificationId);
                pushBadgeCount(recipientId);
            }
        });
    }

    public void deleteReadNotifications(Long recipientId) {
        int deleted = notificationRepository.deleteReadByRecipient(recipientId);
        log.info("Deleted {} read notifications for user {}", deleted, recipientId);
    }

    private void pushWebSocketNotification(Long recipientId, Notification notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/notifications",
                    Map.of(
                            "notificationId", notification.getNotificationId(),
                            "type",           notification.getType().name(),
                            "title",          notification.getTitle(),
                            "message",        notification.getMessage() != null ? notification.getMessage() : "",
                            "deepLinkUrl",    notification.getDeepLinkUrl() != null ? notification.getDeepLinkUrl() : "",
                            "relatedId",      notification.getRelatedId() != null ? notification.getRelatedId() : "",
                            "createdAt",      notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "",
                            "isRead",         false
                    )
            );
        } catch (Exception e) {
            log.warn("WebSocket push failed for user {}: {}", recipientId, e.getMessage());
        }
        pushBadgeCount(recipientId);
    }

    private void pushBadgeCount(Long recipientId) {
        try {
            int count = notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/badge",
                    Map.of("unreadCount", count, "userId", recipientId)
            );
        } catch (Exception e) {
            log.warn("Badge WebSocket push failed for user {}: {}", recipientId, e.getMessage());
        }
    }

    private void incrementBadge(Long recipientId) {
        try {
            String key = BADGE_KEY_PREFIX + recipientId;
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, BADGE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis unavailable, badge not incremented for user {}: {}", recipientId, e.getMessage());
        }
    }

    private void decrementBadge(Long recipientId) {
        try {
            String key = BADGE_KEY_PREFIX + recipientId;
            Long current = redisTemplate.opsForValue().decrement(key);
            if (current != null && current < 0) {
                redisTemplate.opsForValue().set(key, "0", BADGE_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, badge not decremented for user {}: {}", recipientId, e.getMessage());
        }
    }
}
