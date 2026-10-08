package com.memme.entity.ranking;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "ranking_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_snapshots_period_revision",
                columnNames = {"period_start", "period_end", "comparison_start", "comparison_end", "revision_no"}
        )
)
public class RankingSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "comparison_start", nullable = false)
    private LocalDate comparisonStart;

    @Column(name = "comparison_end", nullable = false)
    private LocalDate comparisonEnd;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RankingSnapshotStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RankingSnapshotEntity() {
    }

    private RankingSnapshotEntity(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate comparisonStart,
            LocalDate comparisonEnd,
            int revisionNo
    ) {
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        this.comparisonStart = Objects.requireNonNull(comparisonStart, "comparisonStart");
        this.comparisonEnd = Objects.requireNonNull(comparisonEnd, "comparisonEnd");
        validatePeriod(periodStart, periodEnd, "period");
        validatePeriod(comparisonStart, comparisonEnd, "comparison period");
        if (revisionNo < 1) {
            throw new IllegalArgumentException("revisionNo must be positive");
        }
        this.revisionNo = revisionNo;
        this.status = RankingSnapshotStatus.PENDING;
    }

    public static RankingSnapshotEntity create(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate comparisonStart,
            LocalDate comparisonEnd,
            int revisionNo
    ) {
        return new RankingSnapshotEntity(periodStart, periodEnd, comparisonStart, comparisonEnd, revisionNo);
    }

    private static void validatePeriod(LocalDate start, LocalDate end, String name) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(name + " start must not be after end");
        }
    }

    public void start() {
        if (status != RankingSnapshotStatus.PENDING) {
            throw new IllegalStateException("only a pending snapshot can start");
        }
        status = RankingSnapshotStatus.PROCESSING;
    }

    public void complete() {
        if (status != RankingSnapshotStatus.PROCESSING) {
            throw new IllegalStateException("only a processing snapshot can complete");
        }
        status = RankingSnapshotStatus.COMPLETED;
        calculatedAt = LocalDateTime.now();
    }

    public void fail() {
        if (status == RankingSnapshotStatus.COMPLETED || status == RankingSnapshotStatus.FAILED) {
            return;
        }
        status = RankingSnapshotStatus.FAILED;
        calculatedAt = LocalDateTime.now();
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public LocalDate getComparisonStart() {
        return comparisonStart;
    }

    public LocalDate getComparisonEnd() {
        return comparisonEnd;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public RankingSnapshotStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
