package com.collabservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "collab_sessions")
public class CollabSession {

    @Id
    private String sessionId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    private String language;

    private int maxParticipants = 10;

    private boolean isPasswordProtected = false;

    private String sessionPassword;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime endedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    public enum Status { ACTIVE, ENDED }

    // ── No-arg constructor ────────────────────────────────────────────────────

    public CollabSession() {}

    // ── All-arg constructor ───────────────────────────────────────────────────

    public CollabSession(String sessionId, Long projectId, Long fileId, Long ownerId,
                         Status status, String language, int maxParticipants,
                         boolean isPasswordProtected, String sessionPassword,
                         LocalDateTime createdAt, LocalDateTime endedAt,
                         List<Participant> participants) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.status = status;
        this.language = language;
        this.maxParticipants = maxParticipants;
        this.isPasswordProtected = isPasswordProtected;
        this.sessionPassword = sessionPassword;
        this.createdAt = createdAt;
        this.endedAt = endedAt;
        this.participants = participants != null ? participants : new ArrayList<>();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getSessionId()                    { return sessionId; }
    public Long getProjectId()                      { return projectId; }
    public Long getFileId()                         { return fileId; }
    public Long getOwnerId()                        { return ownerId; }
    public Status getStatus()                       { return status; }
    public String getLanguage()                     { return language; }
    public int getMaxParticipants()                 { return maxParticipants; }
    public boolean isPasswordProtected()            { return isPasswordProtected; }
    public String getSessionPassword()              { return sessionPassword; }
    public LocalDateTime getCreatedAt()             { return createdAt; }
    public LocalDateTime getEndedAt()               { return endedAt; }
    public List<Participant> getParticipants()      { return participants; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSessionId(String sessionId)                      { this.sessionId = sessionId; }
    public void setProjectId(Long projectId)                        { this.projectId = projectId; }
    public void setFileId(Long fileId)                              { this.fileId = fileId; }
    public void setOwnerId(Long ownerId)                            { this.ownerId = ownerId; }
    public void setStatus(Status status)                            { this.status = status; }
    public void setLanguage(String language)                        { this.language = language; }
    public void setMaxParticipants(int maxParticipants)             { this.maxParticipants = maxParticipants; }
    public void setPasswordProtected(boolean isPasswordProtected)   { this.isPasswordProtected = isPasswordProtected; }
    public void setSessionPassword(String sessionPassword)          { this.sessionPassword = sessionPassword; }
    public void setCreatedAt(LocalDateTime createdAt)               { this.createdAt = createdAt; }
    public void setEndedAt(LocalDateTime endedAt)                   { this.endedAt = endedAt; }
    public void setParticipants(List<Participant> participants)      { this.participants = participants; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sessionId;
        private Long projectId;
        private Long fileId;
        private Long ownerId;
        private Status status = Status.ACTIVE;
        private String language;
        private int maxParticipants = 10;
        private boolean isPasswordProtected = false;
        private String sessionPassword;
        private LocalDateTime createdAt;
        private LocalDateTime endedAt;
        private List<Participant> participants = new ArrayList<>();

        public Builder sessionId(String v)              { this.sessionId = v; return this; }
        public Builder projectId(Long v)                { this.projectId = v; return this; }
        public Builder fileId(Long v)                   { this.fileId = v; return this; }
        public Builder ownerId(Long v)                  { this.ownerId = v; return this; }
        public Builder status(Status v)                 { this.status = v; return this; }
        public Builder language(String v)               { this.language = v; return this; }
        public Builder maxParticipants(int v)           { this.maxParticipants = v; return this; }
        public Builder isPasswordProtected(boolean v)   { this.isPasswordProtected = v; return this; }
        public Builder sessionPassword(String v)        { this.sessionPassword = v; return this; }
        public Builder createdAt(LocalDateTime v)       { this.createdAt = v; return this; }
        public Builder endedAt(LocalDateTime v)         { this.endedAt = v; return this; }
        public Builder participants(List<Participant> v){ this.participants = v; return this; }

        public CollabSession build() {
            return new CollabSession(sessionId, projectId, fileId, ownerId, status, language,
                    maxParticipants, isPasswordProtected, sessionPassword,
                    createdAt, endedAt, participants);
        }
    }
}
