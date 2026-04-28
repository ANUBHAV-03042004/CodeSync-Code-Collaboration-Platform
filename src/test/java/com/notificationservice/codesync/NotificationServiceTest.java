package com.notificationservice.codesync;

import com.notificationservice.codesync.entity.Notification;
import com.notificationservice.codesync.repository.NotificationRepository;
import com.notificationservice.codesync.service.NotificationService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository  notificationRepository;
    @Mock private SimpMessagingTemplate   messagingTemplate;
    @Mock private StringRedisTemplate     redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private NotificationService notificationService;

    // ── helper ────────────────────────────────────────────────────────────────

    private Notification saved(Long id, Long recipientId, Notification.NotificationType type) {
        Notification n = new Notification();
        n.setNotificationId(id);
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle("Test");
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    // ── Test 1: send() persists and pushes WebSocket ──────────────────────────

    @Test
    @DisplayName("send() saves notification and pushes WebSocket badge")
    void send_persistsAndPushesWebSocket() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Notification savedN = saved(1L, 42L, Notification.NotificationType.COMMENT);
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedN);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(42L)).thenReturn(1);

        Notification result = notificationService.send(
                42L, 7L, Notification.NotificationType.COMMENT,
                "New comment", "Someone commented", null, null, null);

        assertThat(result.getNotificationId()).isEqualTo(1L);
        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("42"), eq("/queue/notifications"), any(Map.class));
    }

    // ── Test 2: getUnreadCount() uses Redis cache ─────────────────────────────

    @Test
    @DisplayName("getUnreadCount() returns cached Redis value without hitting DB")
    void getUnreadCount_returnsCachedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("notif:badge:5")).thenReturn("3");

        int count = notificationService.getUnreadCount(5L);

        assertThat(count).isEqualTo(3);
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(anyLong());
    }

    // ── Test 3: getUnreadCount() falls back to DB when cache miss ─────────────

    @Test
    @DisplayName("getUnreadCount() queries DB and populates Redis on cache miss")
    void getUnreadCount_fallsBackToDbOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("notif:badge:5")).thenReturn(null);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(5L)).thenReturn(7);

        int count = notificationService.getUnreadCount(5L);

        assertThat(count).isEqualTo(7);
        verify(valueOps).set(eq("notif:badge:5"), eq("7"), eq(24L), eq(TimeUnit.HOURS));
    }

    // ── Test 4: markAsRead() marks and decrements badge ──────────────────────

    @Test
    @DisplayName("markAsRead() sets isRead=true and decrements Redis badge")
    void markAsRead_setsReadAndDecrementsBadge() {
        Notification n = saved(10L, 42L, Notification.NotificationType.MENTION);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenReturn(n);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.decrement("notif:badge:42")).thenReturn(2L);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(42L)).thenReturn(2);

        notificationService.markAsRead(10L, 42L);

        assertThat(n.isRead()).isTrue();
        verify(notificationRepository).save(n);
        verify(valueOps).decrement("notif:badge:42");
    }

    // ── Test 5: markAllRead() resets badge to zero ────────────────────────────

    @Test
    @DisplayName("markAllRead() resets Redis badge to 0 and pushes badge update")
    void markAllRead_resetsBadgeToZero() {
        when(notificationRepository.markAllReadByRecipient(42L)).thenReturn(5);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(42L)).thenReturn(0);

        notificationService.markAllRead(42L);

        verify(valueOps).set(eq("notif:badge:42"), eq("0"), eq(24L), eq(TimeUnit.HOURS));
        verify(messagingTemplate).convertAndSendToUser(
                eq("42"), eq("/queue/badge"), any(Map.class));
    }

    // ── Test 6: sendBulk() sends BROADCAST to each recipient ─────────────────

    @Test
    @DisplayName("sendBulk() sends BROADCAST notification to every recipient in list")
    void sendBulk_sendsToAllRecipients() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    n.setNotificationId(new Random().nextLong());
                    n.setCreatedAt(LocalDateTime.now());
                    return n;
                });
        when(notificationRepository.countByRecipientIdAndIsReadFalse(anyLong())).thenReturn(1);

        notificationService.sendBulk(List.of(1L, 2L, 3L), 99L,
                "Platform update", "Maintenance tonight", null);

        verify(notificationRepository, times(3)).save(argThat(n ->
                n.getType() == Notification.NotificationType.BROADCAST));
    }
}