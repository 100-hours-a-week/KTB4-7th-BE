package com.memme.entity.sales;

import java.time.LocalDate;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "sales_uploads",
        indexes = {
                @Index(name = "idx_sales_uploads_store_uploaded_at", columnList = "store_id, uploaded_at"),
                @Index(name = "idx_sales_uploads_store_checksum", columnList = "store_id, file_checksum")
        }
)
public class SalesUploadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SalesUploadSourceType sourceType;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "file_checksum", nullable = false, length = 64)
    private String fileChecksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SalesUploadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_phase", length = 20)
    private SalesUploadProcessingPhase processingPhase;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "total_row_count")
    private Integer totalRowCount;

    @Column(name = "applied_record_count")
    private Integer appliedRecordCount;

    @Column(name = "invalid_row_count")
    private Integer invalidRowCount;

    @Column(name = "fail_reason", length = 50)
    private String failReason;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected SalesUploadEntity() {
    }

    private SalesUploadEntity(
            Long storeId,
            Long requestedByUserId,
            String originalFileName,
            String storageKey,
            String fileChecksum
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.requestedByUserId = Objects.requireNonNull(requestedByUserId, "requestedByUserId");
        this.sourceType = SalesUploadSourceType.TOSS_POS;
        this.originalFileName = requireText(originalFileName, "originalFileName");
        this.storageKey = requireText(storageKey, "storageKey");
        this.fileChecksum = requireText(fileChecksum, "fileChecksum");
        this.status = SalesUploadStatus.PENDING;
    }

    public static SalesUploadEntity pending(
            Long storeId,
            Long requestedByUserId,
            String originalFileName,
            String storageKey,
            String fileChecksum
    ) {
        return new SalesUploadEntity(
                storeId,
                requestedByUserId,
                originalFileName,
                storageKey,
                fileChecksum
        );
    }

    public void startProcessing() {
        requireStatus(SalesUploadStatus.PENDING);
        status = SalesUploadStatus.PROCESSING;
        processingPhase = SalesUploadProcessingPhase.VALIDATING;
    }

    public void advancePhase(SalesUploadProcessingPhase phase) {
        requireStatus(SalesUploadStatus.PROCESSING);
        this.processingPhase = Objects.requireNonNull(phase, "phase");
    }

    public void setCoverage(LocalDate periodStart, LocalDate periodEnd, int totalRowCount) {
        requireStatus(SalesUploadStatus.PROCESSING);
        validatePeriod(periodStart, periodEnd);
        if (totalRowCount < 0) {
            throw new IllegalArgumentException("totalRowCount must not be negative");
        }
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalRowCount = totalRowCount;
    }

    public void complete(int appliedRecordCount) {
        requireStatus(SalesUploadStatus.PROCESSING);
        if (appliedRecordCount < 0) {
            throw new IllegalArgumentException("record counts must not be negative");
        }
        this.appliedRecordCount = appliedRecordCount;
        this.invalidRowCount = 0;
        this.status = SalesUploadStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void fail(String failReason, String errorMessage, Integer invalidRowCount) {
        if (status == SalesUploadStatus.COMPLETED || status == SalesUploadStatus.FAILED) {
            throw new IllegalStateException("completed upload cannot transition to failed");
        }
        if (invalidRowCount != null && invalidRowCount < 0) {
            throw new IllegalArgumentException("invalidRowCount must not be negative");
        }
        this.failReason = requireText(failReason, "failReason");
        this.errorMessage = requireText(errorMessage, "errorMessage");
        this.invalidRowCount = invalidRowCount;
        this.status = SalesUploadStatus.FAILED;
        this.processedAt = LocalDateTime.now();
    }

    @PrePersist
    private void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    private void requireStatus(SalesUploadStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("upload status must be " + expected + " but was " + status);
        }
    }

    private static void validatePeriod(LocalDate periodStart, LocalDate periodEnd) {
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must not be after periodEnd");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Long getRequestedByUserId() {
        return requestedByUserId;
    }

    public SalesUploadSourceType getSourceType() {
        return sourceType;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public SalesUploadStatus getStatus() {
        return status;
    }

    public SalesUploadProcessingPhase getProcessingPhase() {
        return processingPhase;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Integer getTotalRowCount() {
        return totalRowCount;
    }

    public Integer getAppliedRecordCount() {
        return appliedRecordCount;
    }

    public Integer getInvalidRowCount() {
        return invalidRowCount;
    }

    public String getFailReason() {
        return failReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
