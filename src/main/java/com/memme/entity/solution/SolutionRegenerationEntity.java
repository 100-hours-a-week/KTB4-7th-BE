package com.memme.entity.solution;

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
import java.util.Objects;

@Entity
@Table(name = "solution_regenerations", uniqueConstraints = @UniqueConstraint(
        name = "uk_solution_regen_store_date_source",
        columnNames = {"store_id", "target_date", "source_fingerprint"}))
public class SolutionRegenerationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;
    @Column(name = "source_fingerprint", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sourceFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SolutionRegenerationStatus status;
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

    protected SolutionRegenerationEntity() {}

    public static SolutionRegenerationEntity start(Long storeId, LocalDate targetDate,
                                                   String sourceFingerprint, LocalDateTime now) {
        if (storeId == null || storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(targetDate, "targetDate");
        Objects.requireNonNull(now, "now");
        if (sourceFingerprint == null || !sourceFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceFingerprint must be lowercase SHA-256 hex");
        }
        var execution = new SolutionRegenerationEntity();
        execution.storeId = storeId;
        execution.targetDate = targetDate;
        execution.sourceFingerprint = sourceFingerprint;
        execution.status = SolutionRegenerationStatus.PROCESSING;
        execution.attemptCount = 1;
        execution.startedAt = now;
        execution.createdAt = now;
        execution.updatedAt = now;
        return execution;
    }

    public void complete(LocalDateTime now) {
        requireStatus(SolutionRegenerationStatus.PROCESSING);
        completedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
        status = SolutionRegenerationStatus.COMPLETED;
    }

    public void fail(LocalDateTime now) {
        requireStatus(SolutionRegenerationStatus.PROCESSING);
        completedAt = null;
        updatedAt = Objects.requireNonNull(now, "now");
        status = SolutionRegenerationStatus.FAILED;
    }

    public void restart(LocalDateTime now) {
        requireStatus(SolutionRegenerationStatus.FAILED);
        startedAt = Objects.requireNonNull(now, "now");
        completedAt = null;
        updatedAt = now;
        attemptCount++;
        status = SolutionRegenerationStatus.PROCESSING;
    }

    private void requireStatus(SolutionRegenerationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("solution regeneration status must be " + expected);
        }
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public SolutionRegenerationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
