package com.memme.entity.sales;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sales_upload_outbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_sales_outbox_event_upload", columnNames = {"event_type", "upload_id"}), indexes = {
        @Index(name = "idx_sales_outbox_ready", columnList = "status, available_at, id"),
        @Index(name = "idx_sales_outbox_expired", columnList = "status, lease_until, id"),
        @Index(name = "idx_sales_outbox_store_order", columnList = "store_id, status, id")})
public class SalesUploadOutboxEntity {
    public enum Status { READY, RUNNING, DONE, DEAD }
    public enum Checkpoint { ACCEPTED, DATA_APPLIED, BASIC_COMPLETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "upload_id", nullable = false) private Long uploadId;
    @Column(name = "analysis_run_id", nullable = false) private Long analysisRunId;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Checkpoint checkpoint;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "available_at", nullable = false) private LocalDateTime availableAt;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "claim_token", length = 36) private String claimToken;
    @Column(name = "lease_owner", length = 100) private String leaseOwner;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "lease_until") private LocalDateTime leaseUntil;
    @Column(name = "applied_record_count") private Integer appliedRecordCount;
    @Column(name = "last_error_code", length = 50) private String lastErrorCode;
    @Column(name = "last_error_message", length = 500) private String lastErrorMessage;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "finished_at") private LocalDateTime finishedAt;

    protected SalesUploadOutboxEntity() {}
    public static SalesUploadOutboxEntity ready(Long storeId, Long uploadId, Long runId, LocalDateTime now) {
        var job = new SalesUploadOutboxEntity();
        job.storeId = storeId; job.uploadId = uploadId; job.analysisRunId = runId;
        job.eventType = "SALES_UPLOAD_BASIC_ANALYSIS";
        job.status = Status.READY; job.checkpoint = Checkpoint.ACCEPTED;
        job.maxAttempts = 5; job.availableAt = now; job.createdAt = now; job.updatedAt = now;
        return job;
    }
    public boolean eligible(LocalDateTime now) {
        return status == Status.READY && !availableAt.isAfter(now)
                || status == Status.RUNNING && !leaseUntil.isAfter(now);
    }
    public SalesUploadOutboxClaim claim(String owner, LocalDateTime now) {
        if (!eligible(now) || attemptCount >= maxAttempts) throw new IllegalStateException("outbox is not claimable");
        status = Status.RUNNING; claimToken = UUID.randomUUID().toString(); leaseOwner = owner;
        leaseUntil = now.plusSeconds(60); attemptCount++; updatedAt = now;
        return new SalesUploadOutboxClaim(id, storeId, uploadId, analysisRunId, claimToken);
    }
    public boolean owns(SalesUploadOutboxClaim claim, LocalDateTime now) {
        return status == Status.RUNNING && id.equals(claim.id()) && storeId.equals(claim.storeId())
                && uploadId.equals(claim.uploadId()) && analysisRunId.equals(claim.analysisRunId())
                && claimToken.equals(claim.token()) && leaseUntil.isAfter(now);
    }
    public void heartbeat(LocalDateTime now) { leaseUntil = now.plusSeconds(60); updatedAt = now; }
    public void dataApplied(int records, LocalDateTime now) {
        if (checkpoint != Checkpoint.ACCEPTED || records < 0) throw new IllegalStateException("invalid checkpoint");
        checkpoint = Checkpoint.DATA_APPLIED; appliedRecordCount = records; heartbeat(now);
    }
    public void complete(LocalDateTime now) {
        if (checkpoint != Checkpoint.DATA_APPLIED) throw new IllegalStateException("data not applied");
        checkpoint = Checkpoint.BASIC_COMPLETED; status = Status.DONE; finish(now);
    }
    public void retry(String code, String message, LocalDateTime available, LocalDateTime now) {
        status = Status.READY; availableAt = available; lastErrorCode = code; lastErrorMessage = message;
        clearLease(); updatedAt = now;
    }
    public void rejected(LocalDateTime now) {
        attemptCount--; retry("EXECUTOR_BUSY", "실행기 등록을 다시 시도합니다.", now.plusSeconds(1), now);
    }
    public void dead(String code, String message, LocalDateTime now) {
        status = Status.DEAD; lastErrorCode = code; lastErrorMessage = message; finish(now);
    }
    private void finish(LocalDateTime now) { clearLease(); finishedAt = now; updatedAt = now; }
    private void clearLease() { claimToken = null; leaseOwner = null; leaseUntil = null; }
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getUploadId() { return uploadId; }
    public Long getAnalysisRunId() { return analysisRunId; }
    public Status getStatus() { return status; }
    public Checkpoint getCheckpoint() { return checkpoint; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Integer getAppliedRecordCount() { return appliedRecordCount; }
    public LocalDateTime getAvailableAt() { return availableAt; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
}
