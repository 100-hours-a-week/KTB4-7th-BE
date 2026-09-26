package com.memme.entity.chat;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_chat_messages_bundle_created", columnList = "solution_bundle_id, created_at")
        }
)
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "solution_bundle_id")
    private Long solutionBundleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ChatMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "evidence_json", columnDefinition = "JSON")
    private String evidenceJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatMessageStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChatMessageEntity() {
    }

    private ChatMessageEntity(
            Long userId,
            Long solutionBundleId,
            ChatMessageRole role,
            String content,
            ChatMessageStatus status,
            LocalDateTime createdAt
    ) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.solutionBundleId = solutionBundleId;
        this.role = Objects.requireNonNull(role, "role");
        this.content = Objects.requireNonNull(content, "content");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ChatMessageEntity user(Long userId, Long bundleId, String content, LocalDateTime createdAt) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return new ChatMessageEntity(
                userId,
                bundleId,
                ChatMessageRole.USER,
                content,
                ChatMessageStatus.COMPLETED,
                createdAt
        );
    }

    public static ChatMessageEntity assistant(Long userId, Long bundleId, LocalDateTime createdAt) {
        return new ChatMessageEntity(
                userId,
                bundleId,
                ChatMessageRole.ASSISTANT,
                "",
                ChatMessageStatus.PENDING,
                createdAt
        );
    }

    public void startStreaming() {
        status = ChatMessageStatus.STREAMING;
    }

    public void complete(String content, String evidenceJson) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        this.content = content;
        this.evidenceJson = evidenceJson;
        this.status = ChatMessageStatus.COMPLETED;
    }

    public void fail() {
        status = ChatMessageStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSolutionBundleId() {
        return solutionBundleId;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public ChatMessageStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
