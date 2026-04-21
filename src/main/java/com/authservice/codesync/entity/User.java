package com.authservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "username")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String passwordHash;

    @Column
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.DEVELOPER;

    @Column
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column
    private String providerId;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(length = 500)
    private String bio;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public User() {}

    private User(Builder b) {
        this.userId       = b.userId;
        this.username     = b.username;
        this.email        = b.email;
        this.passwordHash = b.passwordHash;
        this.fullName     = b.fullName;
        this.role         = b.role != null ? b.role : Role.DEVELOPER;
        this.avatarUrl    = b.avatarUrl;
        this.provider     = b.provider != null ? b.provider : AuthProvider.LOCAL;
        this.providerId   = b.providerId;
        this.isActive     = b.isActive;
        this.bio          = b.bio;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long         userId;
        private String       username;
        private String       email;
        private String       passwordHash;
        private String       fullName;
        private Role         role;
        private String       avatarUrl;
        private AuthProvider provider;
        private String       providerId;
        private boolean      isActive = true;
        private String       bio;

        public Builder userId(Long v)           { this.userId       = v; return this; }
        public Builder username(String v)       { this.username     = v; return this; }
        public Builder email(String v)          { this.email        = v; return this; }
        public Builder passwordHash(String v)   { this.passwordHash = v; return this; }
        public Builder fullName(String v)       { this.fullName     = v; return this; }
        public Builder role(Role v)             { this.role         = v; return this; }
        public Builder avatarUrl(String v)      { this.avatarUrl    = v; return this; }
        public Builder provider(AuthProvider v) { this.provider     = v; return this; }
        public Builder providerId(String v)     { this.providerId   = v; return this; }
        public Builder isActive(boolean v)      { this.isActive     = v; return this; }
        public Builder bio(String v)            { this.bio          = v; return this; }

        public User build() { return new User(this); }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getUserId()                         { return userId; }
    public void setUserId(Long userId)              { this.userId = userId; }

    public String getUsername()                     { return username; }
    public void setUsername(String username)        { this.username = username; }

    public String getEmail()                        { return email; }
    public void setEmail(String email)              { this.email = email; }

    public String getPasswordHash()                 { return passwordHash; }
    public void setPasswordHash(String passwordHash){ this.passwordHash = passwordHash; }

    public String getFullName()                     { return fullName; }
    public void setFullName(String fullName)        { this.fullName = fullName; }

    public Role getRole()                           { return role; }
    public void setRole(Role role)                  { this.role = role; }

    public String getAvatarUrl()                    { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl)      { this.avatarUrl = avatarUrl; }

    public AuthProvider getProvider()               { return provider; }
    public void setProvider(AuthProvider provider)  { this.provider = provider; }

    public String getProviderId()                   { return providerId; }
    public void setProviderId(String providerId)    { this.providerId = providerId; }

    public boolean isActive()                       { return isActive; }
    public void setActive(boolean isActive)         { this.isActive = isActive; }

    public String getBio()                          { return bio; }
    public void setBio(String bio)                  { this.bio = bio; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime v)       { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()             { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)       { this.updatedAt = v; }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum Role {
        DEVELOPER, ADMIN
    }

    public enum AuthProvider {
        LOCAL, GITHUB, GOOGLE
    }
}
