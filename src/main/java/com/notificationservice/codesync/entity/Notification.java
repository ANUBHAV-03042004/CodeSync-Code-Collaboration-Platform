package com.notificationservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_notif_recipient", columnList = "recipientId"),
           @Index(name = "idx_notif_unread",    columnList = "recipientId, isRead"),
           @Index(name = "idx_notif_type",      columnList = "type")
       })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Column(nullable = false)
    private Long recipientId;

    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String relatedId;
    private String relatedType;
    private String deepLinkUrl;

    private boolean isRead = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Notification() {}

    private Notification(Builder b) {
        this.recipientId  = b.recipientId;
        this.actorId      = b.actorId;
        this.type         = b.type;
        this.title        = b.title;
        this.message      = b.message;
        this.relatedId    = b.relatedId;
        this.relatedType  = b.relatedType;
        this.deepLinkUrl  = b.deepLinkUrl;
        this.isRead       = b.isRead;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long             recipientId;
        private Long             actorId;
        private NotificationType type;
        private String           title;
        private String           message;
        private String           relatedId;
        private String           relatedType;
        private String           deepLinkUrl;
        private boolean          isRead = false;

        private Builder() {}

        public Builder recipientId(Long v)          { this.recipientId = v;  return this; }
        public Builder actorId(Long v)              { this.actorId = v;      return this; }
        public Builder type(NotificationType v)     { this.type = v;         return this; }
        public Builder title(String v)              { this.title = v;        return this; }
        public Builder message(String v)            { this.message = v;      return this; }
        public Builder relatedId(String v)          { this.relatedId = v;    return this; }
        public Builder relatedType(String v)        { this.relatedType = v;  return this; }
        public Builder deepLinkUrl(String v)        { this.deepLinkUrl = v;  return this; }
        public Builder isRead(boolean v)            { this.isRead = v;       return this; }
        public Notification build()                 { return new Notification(this); }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long             getNotificationId() { return notificationId; }
    public Long             getRecipientId()    { return recipientId; }
    public Long             getActorId()        { return actorId; }
    public NotificationType getType()           { return type; }
    public String           getTitle()          { return title; }
    public String           getMessage()        { return message; }
    public String           getRelatedId()      { return relatedId; }
    public String           getRelatedType()    { return relatedType; }
    public String           getDeepLinkUrl()    { return deepLinkUrl; }
    public boolean          isRead()            { return isRead; }
    public LocalDateTime    getCreatedAt()      { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setNotificationId(Long v)       { this.notificationId = v; }
    public void setRecipientId(Long v)          { this.recipientId = v; }
    public void setActorId(Long v)              { this.actorId = v; }
    public void setType(NotificationType v)     { this.type = v; }
    public void setTitle(String v)              { this.title = v; }
    public void setMessage(String v)            { this.message = v; }
    public void setRelatedId(String v)          { this.relatedId = v; }
    public void setRelatedType(String v)        { this.relatedType = v; }
    public void setDeepLinkUrl(String v)        { this.deepLinkUrl = v; }
    public void setRead(boolean v)              { this.isRead = v; }
    public void setCreatedAt(LocalDateTime v)   { this.createdAt = v; }

    // ── Enum ─────────────────────────────────────────────────────────────────

    public enum NotificationType {
        SESSION_INVITE, PARTICIPANT_JOINED, PARTICIPANT_LEFT,
        COMMENT, MENTION, SNAPSHOT, FORK, BROADCAST
    }

    @Override
    public String toString() {
        return "Notification{id=" + notificationId + ", type=" + type +
               ", recipientId=" + recipientId + ", isRead=" + isRead + '}';
    }
}