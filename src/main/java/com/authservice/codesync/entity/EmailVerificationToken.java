package com.authservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * One-time token stored when a LOCAL user registers.
 * The user clicks the link in their email → backend validates → account activated.
 * Token expires after 24 hours; a new one can be requested via /resend-verification.
 */
@Entity
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_evt_token", columnList = "token", unique = true),
        @Index(name = "idx_evt_user",  columnList = "user_id")
})
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL-safe Base64 token sent in the verification email. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected EmailVerificationToken() {}

    public EmailVerificationToken(String token, User user, LocalDateTime expiresAt) {
        this.token     = token;
        this.user      = user;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }

    public Long getId()                 { return id; }
    public String getToken()            { return token; }
    public User getUser()               { return user; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed()             { return used; }
    public void setUsed(boolean used)   { this.used = used; }
}
