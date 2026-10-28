package com.memme.entity.noti;

import com.memme.entity.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "fcm_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_fcm_tokens_hash", columnNames = "token_hash")
)
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_value", nullable = false, length = 4096)
    private String tokenValue;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FcmToken() {
    }

    public static FcmToken register(User user, String tokenValue, String tokenHash, LocalDateTime registeredAt) {
        FcmToken token = new FcmToken();
        token.user = Objects.requireNonNull(user, "user");
        token.tokenValue = Objects.requireNonNull(tokenValue, "tokenValue");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        token.lastRegisteredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        token.createdAt = registeredAt;
        token.updatedAt = registeredAt;
        return token;
    }

    public void refresh(String tokenValue, String tokenHash, LocalDateTime registeredAt) {
        this.tokenValue = Objects.requireNonNull(tokenValue, "tokenValue");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.lastRegisteredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.updatedAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getLastRegisteredAt() {
        return lastRegisteredAt;
    }

    @Override
    public String toString() {
        return "FcmToken[id=" + id + ", userId=" + (user == null ? null : user.getId())
                + ", tokenValue=<redacted>, tokenHash=<redacted>]";
    }
}
