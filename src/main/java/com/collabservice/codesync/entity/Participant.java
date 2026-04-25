package com.collabservice.codesync.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "participants")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private CollabSession session;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private Role role = Role.EDITOR;

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    private int cursorLine;
    private int cursorCol;

    private String color;

    public enum Role { HOST, EDITOR, VIEWER }

    // ── No-arg constructor ────────────────────────────────────────────────────

    public Participant() {}

    // ── All-arg constructor ───────────────────────────────────────────────────

    public Participant(Long participantId, CollabSession session, Long userId, Role role,
                       LocalDateTime joinedAt, LocalDateTime leftAt,
                       int cursorLine, int cursorCol, String color) {
        this.participantId = participantId;
        this.session = session;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.cursorLine = cursorLine;
        this.cursorCol = cursorCol;
        this.color = color;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getParticipantId()          { return participantId; }
    public CollabSession getSession()       { return session; }
    public Long getUserId()                 { return userId; }
    public Role getRole()                   { return role; }
    public LocalDateTime getJoinedAt()      { return joinedAt; }
    public LocalDateTime getLeftAt()        { return leftAt; }
    public int getCursorLine()              { return cursorLine; }
    public int getCursorCol()               { return cursorCol; }
    public String getColor()                { return color; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setParticipantId(Long participantId)    { this.participantId = participantId; }
    public void setSession(CollabSession session)        { this.session = session; }
    public void setUserId(Long userId)                   { this.userId = userId; }
    public void setRole(Role role)                       { this.role = role; }
    public void setJoinedAt(LocalDateTime joinedAt)      { this.joinedAt = joinedAt; }
    public void setLeftAt(LocalDateTime leftAt)          { this.leftAt = leftAt; }
    public void setCursorLine(int cursorLine)             { this.cursorLine = cursorLine; }
    public void setCursorCol(int cursorCol)               { this.cursorCol = cursorCol; }
    public void setColor(String color)                   { this.color = color; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long participantId;
        private CollabSession session;
        private Long userId;
        private Role role = Role.EDITOR;
        private LocalDateTime joinedAt;
        private LocalDateTime leftAt;
        private int cursorLine;
        private int cursorCol;
        private String color;

        public Builder participantId(Long v)        { this.participantId = v; return this; }
        public Builder session(CollabSession v)     { this.session = v; return this; }
        public Builder userId(Long v)               { this.userId = v; return this; }
        public Builder role(Role v)                 { this.role = v; return this; }
        public Builder joinedAt(LocalDateTime v)    { this.joinedAt = v; return this; }
        public Builder leftAt(LocalDateTime v)      { this.leftAt = v; return this; }
        public Builder cursorLine(int v)            { this.cursorLine = v; return this; }
        public Builder cursorCol(int v)             { this.cursorCol = v; return this; }
        public Builder color(String v)              { this.color = v; return this; }

        public Participant build() {
            return new Participant(participantId, session, userId, role,
                    joinedAt, leftAt, cursorLine, cursorCol, color);
        }
    }
}
