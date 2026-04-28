package com.notificationservice.codesync.controller;

import com.notificationservice.codesync.entity.Notification;
import com.notificationservice.codesync.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationResource {

    private final NotificationService notificationService;

    public NotificationResource(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getAll(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(notificationService.getByRecipient(Long.parseLong(userId)));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnread(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(notificationService.getUnread(Long.parseLong(userId)));
    }

    @GetMapping("/badge")
    public ResponseEntity<Map<String, Integer>> getBadgeCount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(Map.of(
                "unreadCount", notificationService.getUnreadCount(Long.parseLong(userId))));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        notificationService.markAsRead(id, Long.parseLong(userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader("X-User-Id") String userId) {
        notificationService.markAllRead(Long.parseLong(userId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        notificationService.deleteNotification(id, Long.parseLong(userId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/read")
    public ResponseEntity<Void> deleteRead(
            @RequestHeader("X-User-Id") String userId) {
        notificationService.deleteReadNotifications(Long.parseLong(userId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(@RequestBody BroadcastRequest req,
                                           @RequestHeader("X-User-Id") String actorId) {
        notificationService.sendBulk(
                req.recipientIds(), Long.parseLong(actorId),
                req.title(), req.message(), req.deepLinkUrl());
        return ResponseEntity.ok().build();
    }

    record BroadcastRequest(List<Long> recipientIds, String title,
                             String message, String deepLinkUrl) {}
}