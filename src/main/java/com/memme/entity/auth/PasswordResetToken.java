package com.memme.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PasswordResetToken() {
    }

    public static PasswordResetToken create(
            User user,
            String tokenHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.user = user;
        passwordResetToken.tokenHash = tokenHash;
        passwordResetToken.expiresAt = expiresAt;
        passwordResetToken.createdAt = createdAt;
        return passwordResetToken;
    }

    public User getUser() {
        return user;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
