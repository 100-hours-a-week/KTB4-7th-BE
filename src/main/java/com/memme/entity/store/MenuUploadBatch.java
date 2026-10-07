package com.memme.entity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "menu_upload_batches",
        indexes = @Index(name = "idx_menu_batch_latest", columnList = "store_id,uploaded_at,id"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_batch_store_idempotency",
                        columnNames = {"store_id", "idempotency_key_hash"}),
                @UniqueConstraint(name = "uk_menu_batch_id_store", columnNames = {"id", "store_id"})
        })
public class MenuUploadBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MenuProcessingStatus status;

    @Column(name = "fail_reason", length = 50)
    private String failReason;

    @Column(name = "base_menu_revision", nullable = false)
    private long baseMenuRevision;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String idempotencyKeyHash;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    protected MenuUploadBatch() {
    }

    public static MenuUploadBatch pending(Long storeId, long baseMenuRevision,
            String idempotencyKeyHash, String requestHash, LocalDateTime uploadedAt) {
        if (baseMenuRevision < 0) {
            throw new IllegalArgumentException("메뉴 개정 번호는 음수일 수 없습니다.");
        }
        MenuUploadBatch batch = new MenuUploadBatch();
        batch.storeId = Objects.requireNonNull(storeId, "storeId");
        batch.baseMenuRevision = baseMenuRevision;
        batch.idempotencyKeyHash = Objects.requireNonNull(idempotencyKeyHash, "idempotencyKeyHash");
        batch.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        batch.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt");
        batch.status = MenuProcessingStatus.PENDING;
        return batch;
    }

    public void startProcessing() {
        requireStatus(MenuProcessingStatus.PENDING);
        status = MenuProcessingStatus.PROCESSING;
    }

    public void complete(LocalDateTime now) {
        requireStatus(MenuProcessingStatus.PROCESSING);
        status = MenuProcessingStatus.COMPLETED;
        completedAt = Objects.requireNonNull(now, "now");
    }

    public void fail(String reason, LocalDateTime now) {
        if (status == MenuProcessingStatus.COMPLETED) {
            throw new IllegalStateException("완료된 배치는 실패 처리할 수 없습니다.");
        }
        status = MenuProcessingStatus.FAILED;
        failReason = Objects.requireNonNull(reason, "reason");
        completedAt = Objects.requireNonNull(now, "now");
    }

    public void markSaved(LocalDateTime now) {
        requireStatus(MenuProcessingStatus.COMPLETED);
        if (savedAt != null || supersededAt != null) {
            throw new IllegalStateException("이미 저장되거나 교체된 배치입니다.");
        }
        savedAt = Objects.requireNonNull(now, "now");
    }

    public void markSuperseded(LocalDateTime now) {
        if (savedAt != null) {
            throw new IllegalStateException("저장된 배치는 교체할 수 없습니다.");
        }
        supersededAt = Objects.requireNonNull(now, "now");
    }

    private void requireStatus(MenuProcessingStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("배치 상태가 " + expected + "가 아닙니다.");
        }
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public MenuProcessingStatus getStatus() { return status; }
    public String getFailReason() { return failReason; }
    public long getBaseMenuRevision() { return baseMenuRevision; }
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public String getRequestHash() { return requestHash; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getSavedAt() { return savedAt; }
    public LocalDateTime getSupersededAt() { return supersededAt; }
}
