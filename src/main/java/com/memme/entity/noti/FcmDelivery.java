package com.memme.entity.noti;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "fcm_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fcm_deliveries_notification_token",
                columnNames = {"notification_id", "token_hash"}
        )
)
public class FcmDelivery {

    public enum Status {
        PENDING,
        SENT,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fcm_token_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private FcmToken fcmToken;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FcmDelivery() {
    }

    public static FcmDelivery pending(
            Notification notification,
            FcmToken fcmToken,
            String tokenHash,
            LocalDateTime createdAt
    ) {
        FcmDelivery delivery = new FcmDelivery();
        delivery.notification = Objects.requireNonNull(notification, "notification");
        delivery.fcmToken = fcmToken;
        delivery.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        delivery.status = Status.PENDING;
        delivery.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        delivery.updatedAt = createdAt;
        return delivery;
    }

    public void markAttemptStarted(LocalDateTime attemptedAt) {
        this.status = Status.PENDING;
        this.attemptCount++;
        this.lastAttemptAt = Objects.requireNonNull(attemptedAt, "attemptedAt");
        this.updatedAt = attemptedAt;
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = Status.SENT;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.lastErrorCode = null;
        this.updatedAt = sentAt;
    }

    public void markFailed(String errorCode, LocalDateTime failedAt) {
        this.status = Status.FAILED;
        this.lastErrorCode = errorCode;
        this.updatedAt = Objects.requireNonNull(failedAt, "failedAt");
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public FcmToken getFcmToken() {
        return fcmToken;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
