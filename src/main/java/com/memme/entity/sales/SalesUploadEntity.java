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
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "sales_uploads",
        indexes = {
                @Index(name = "idx_sales_uploads_store_uploaded_at", columnList = "store_id, uploaded_at"),
                @Index(name = "idx_sales_uploads_store_period", columnList = "store_id, period_start, period_end"),
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

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
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

    @Column(name = "total_row_count", nullable = false)
    private Long totalRowCount;

    @Column(name = "valid_row_count", nullable = false)
    private Long validRowCount;

    @Column(name = "invalid_row_count", nullable = false)
    private Long invalidRowCount;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
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
        this.originalFileName = requireText(originalFileName, "originalFileName");
        this.storageKey = requireText(storageKey, "storageKey");
        this.fileChecksum = requireText(fileChecksum, "fileChecksum");
        this.status = SalesUploadStatus.PENDING;
        this.totalRowCount = 0L;
        this.validRowCount = 0L;
        this.invalidRowCount = 0L;
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

    public void setCoverage(LocalDate periodStart, LocalDate periodEnd, long totalRowCount) {
        requireStatus(SalesUploadStatus.PROCESSING);
        validatePeriod(periodStart, periodEnd);
        if (totalRowCount < 0) {
            throw new IllegalArgumentException("totalRowCount must not be negative");
        }
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalRowCount = totalRowCount;
    }

    public void complete(long validRowCount) {
        requireStatus(SalesUploadStatus.PROCESSING);
        if (validRowCount < 0 || validRowCount > totalRowCount) {
            throw new IllegalArgumentException("validRowCount must be between zero and totalRowCount");
        }
        this.validRowCount = validRowCount;
        this.invalidRowCount = totalRowCount - validRowCount;
        this.status = SalesUploadStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage, Long invalidRowCount) {
        if (status == SalesUploadStatus.COMPLETED || status == SalesUploadStatus.FAILED) {
            throw new IllegalStateException("completed upload cannot transition to failed");
        }
        if (invalidRowCount != null && invalidRowCount < 0) {
            throw new IllegalArgumentException("invalidRowCount must not be negative");
        }
        this.errorCode = requireText(errorCode, "errorCode");
        this.errorMessage = requireText(errorMessage, "errorMessage");
        if (invalidRowCount != null) {
            this.invalidRowCount = invalidRowCount;
            this.validRowCount = Math.max(0L, totalRowCount - invalidRowCount);
        }
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

    public Long getTotalRowCount() {
        return totalRowCount;
    }

    public Long getValidRowCount() {
        return validRowCount;
    }

    public Long getAppliedRecordCount() {
        return validRowCount;
    }

    public Long getInvalidRowCount() {
        return invalidRowCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getFailReason() {
        return errorCode;
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
