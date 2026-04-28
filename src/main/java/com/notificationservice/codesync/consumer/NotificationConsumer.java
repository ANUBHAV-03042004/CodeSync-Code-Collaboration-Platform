package com.notificationservice.codesync.consumer;

import com.notificationservice.codesync.entity.Notification;
import com.notificationservice.codesync.service.EmailService;
import com.notificationservice.codesync.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final EmailService        emailService;

    public NotificationConsumer(NotificationService notificationService,
                                 EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService        = emailService;
    }

    @RabbitListener(queues = "codesync.notification.inapp.queue",
                    containerFactory = "rabbitListenerContainerFactory")
    public void handleInAppNotification(Map<String, Object> event,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String type = (String) event.get("type");
        log.info("In-app notification received [type={}]", type);
        try {
            Long recipientId = toLong(event.get("recipientId"));
            if (recipientId == null) {
                log.warn("Skipping — recipientId is null");
                channel.basicAck(deliveryTag, false);
                return;
            }
            notificationService.send(
                    recipientId,
                    toLong(event.get("actorId")),
                    resolveType(type),
                    (String) event.getOrDefault("title",   "New notification"),
                    (String) event.getOrDefault("message", ""),
                    (String) event.getOrDefault("relatedId",   null),
                    (String) event.getOrDefault("relatedType", null),
                    (String) event.getOrDefault("deepLinkUrl", null)
            );
            channel.basicAck(deliveryTag, false);
            log.debug("In-app notification processed for user {}", recipientId);
        } catch (Exception e) {
            log.error("Failed to process in-app notification [type={}]: {}", type, e.getMessage(), e);
            nackToDlq(channel, deliveryTag);
        }
    }

    @RabbitListener(queues = "codesync.notification.email.queue",
                    containerFactory = "rabbitListenerContainerFactory")
    public void handleEmailNotification(Map<String, Object> event,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String type = (String) event.get("type");
        log.info("Email notification received [type={}]", type);
        try {
            String recipientEmail = (String) event.get("recipientEmail");
            if (recipientEmail == null) {
                log.warn("Skipping email — recipientEmail not provided");
                channel.basicAck(deliveryTag, false);
                return;
            }
            switch (type != null ? type : "") {
                case "MENTION" -> emailService.sendMentionEmail(
                        recipientEmail,
                        (String) event.getOrDefault("actorUsername", "Someone"),
                        (String) event.getOrDefault("message",       ""),
                        (String) event.getOrDefault("deepLinkUrl",   ""));
                case "SESSION_INVITE" -> emailService.sendSessionInviteEmail(
                        recipientEmail,
                        (String) event.getOrDefault("actorUsername", "Someone"),
                        (String) event.getOrDefault("projectName",   "a project"),
                        (String) event.getOrDefault("deepLinkUrl",   ""));
                default -> emailService.sendTemplatedEmail(
                        recipientEmail,
                        (String) event.getOrDefault("title", "New notification from CodeSync"),
                        "generic-notification", event);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to send email [type={}]: {}", type, e.getMessage(), e);
            nackToDlq(channel, deliveryTag);
        }
    }

    @RabbitListener(queues = "codesync.notification.dlq")
    public void handleDeadLetter(Map<String, Object> event) {
        log.error("DEAD LETTER — notification failed permanently [type={}] recipient={}",
                event.get("type"), event.get("recipientId"));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l)    return l;
        if (value instanceof Integer i) return i.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Notification.NotificationType resolveType(String type) {
        try { return Notification.NotificationType.valueOf(type); }
        catch (Exception e) { return Notification.NotificationType.COMMENT; }
    }

    private void nackToDlq(Channel channel, long deliveryTag) {
        try { channel.basicNack(deliveryTag, false, false); }
        catch (IOException e) { log.error("Failed to nack", e); }
    }
}