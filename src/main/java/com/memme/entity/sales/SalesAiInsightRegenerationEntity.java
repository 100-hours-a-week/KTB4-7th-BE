package com.memme.entity.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

@Entity
@Table(name = "sales_ai_insight_regenerations", uniqueConstraints = @UniqueConstraint(
        name = "uk_sales_ai_insight_regen_store_month_source",
        columnNames = {"store_id", "target_month", "source_fingerprint"}))
public class SalesAiInsightRegenerationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Column(name = "target_month", nullable = false)
    private LocalDate targetMonth;
    @Column(name = "source_fingerprint", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sourceFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SalesAiInsightRegenerationStatus status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Version @Column(name = "version", nullable = false)
    private long version;

    protected SalesAiInsightRegenerationEntity() {}

    public static SalesAiInsightRegenerationEntity start(Long storeId, YearMonth targetMonth,
                                                         String sourceFingerprint, LocalDateTime now) {
        requireKey(storeId, sourceFingerprint);
        Objects.requireNonNull(targetMonth, "targetMonth");
        Objects.requireNonNull(now, "now");
        var execution = new SalesAiInsightRegenerationEntity();
        execution.storeId = storeId;
        execution.targetMonth = targetMonth.atDay(1);
        execution.sourceFingerprint = sourceFingerprint;
        execution.status = SalesAiInsightRegenerationStatus.PROCESSING;
        execution.attemptCount = 1;
        execution.startedAt = now;
        execution.createdAt = now;
        execution.updatedAt = now;
        return execution;
    }

    private static void requireKey(Long storeId, String sourceFingerprint) {
        if (storeId == null || storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        if (sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceFingerprint must be lowercase SHA-256 hex");
        }
    }

    public void complete(LocalDateTime now) {
        requireStatus(SalesAiInsightRegenerationStatus.PROCESSING);
        completedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
        status = SalesAiInsightRegenerationStatus.COMPLETED;
    }

    public void fail(LocalDateTime now) {
        requireStatus(SalesAiInsightRegenerationStatus.PROCESSING);
        completedAt = null;
        updatedAt = Objects.requireNonNull(now, "now");
        status = SalesAiInsightRegenerationStatus.FAILED;
    }

    public void restart(LocalDateTime now) {
        requireStatus(SalesAiInsightRegenerationStatus.FAILED);
        startedAt = Objects.requireNonNull(now, "now");
        completedAt = null;
        updatedAt = now;
        attemptCount++;
        status = SalesAiInsightRegenerationStatus.PROCESSING;
    }

    private void requireStatus(SalesAiInsightRegenerationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("insight regeneration status must be " + expected);
        }
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public LocalDate getTargetMonth() { return targetMonth; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public SalesAiInsightRegenerationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
