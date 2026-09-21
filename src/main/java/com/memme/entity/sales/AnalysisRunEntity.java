package com.memme.entity.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis_runs")
public class AnalysisRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "requested_by_user_id", nullable = false) private Long requestedByUserId;
    @Column(name = "based_on_upload_id", nullable = false) private Long basedOnUploadId;
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 20) private AnalysisType analysisType;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private AnalysisRunStatus status;
    @Column(name = "period_start") private LocalDate periodStart;
    @Column(name = "period_end") private LocalDate periodEnd;
    @Column(name = "engine_version", nullable = false, length = 50) private String engineVersion;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100) private String idempotencyKey;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    protected AnalysisRunEntity() {}

    private AnalysisRunEntity(Long storeId, Long requestedByUserId, Long basedOnUploadId) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.requestedByUserId = Objects.requireNonNull(requestedByUserId, "requestedByUserId");
        this.basedOnUploadId = Objects.requireNonNull(basedOnUploadId, "basedOnUploadId");
        this.analysisType = AnalysisType.SALES;
        this.status = AnalysisRunStatus.PENDING;
        this.engineVersion = "sales-v1";
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    public static AnalysisRunEntity pending(Long storeId, Long requestedByUserId, Long uploadId) {
        return new AnalysisRunEntity(storeId, requestedByUserId, uploadId);
    }

    public void start(LocalDate start, LocalDate end) {
        if (status != AnalysisRunStatus.PENDING) throw new IllegalStateException("analysis run must be PENDING");
        if (start == null || end == null || start.isAfter(end)) throw new IllegalArgumentException("invalid period");
        periodStart = start;
        periodEnd = end;
        status = AnalysisRunStatus.PROCESSING;
        startedAt = LocalDateTime.now();
    }

    public void complete() {
        if (status != AnalysisRunStatus.PROCESSING) throw new IllegalStateException("analysis run must be PROCESSING");
        status = AnalysisRunStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        if (status == AnalysisRunStatus.COMPLETED || status == AnalysisRunStatus.FAILED) return;
        errorMessage = message;
        status = AnalysisRunStatus.FAILED;
        completedAt = LocalDateTime.now();
    }

    @PrePersist private void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getBasedOnUploadId() { return basedOnUploadId; }
    public AnalysisRunStatus getStatus() { return status; }
    public String getEngineVersion() { return engineVersion; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
}
