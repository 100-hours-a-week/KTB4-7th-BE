package com.memme.entity.solution;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "solution_bundles",
        indexes = @Index(
                name = "idx_solution_bundle_analysis",
                columnList = "sales_analysis_id"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_solution_bundle_store_date",
                columnNames = {"store_id", "target_date"}
        )
)
public class SolutionBundleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "sales_analysis_id", nullable = false)
    private Long salesAnalysisId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SolutionBundleStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SolutionBundleEntity() {
    }

    private SolutionBundleEntity(Long storeId, Long salesAnalysisId, LocalDate targetDate) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.salesAnalysisId = Objects.requireNonNull(salesAnalysisId, "salesAnalysisId");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate");
        this.status = SolutionBundleStatus.PENDING;
    }

    public static SolutionBundleEntity create(
            Long storeId,
            Long salesAnalysisId,
            LocalDate targetDate
    ) {
        return new SolutionBundleEntity(storeId, salesAnalysisId, targetDate);
    }

    public void changeStatus(SolutionBundleStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void restartForAnalysis(Long salesAnalysisId) {
        this.salesAnalysisId = Objects.requireNonNull(salesAnalysisId, "salesAnalysisId");
        this.status = SolutionBundleStatus.GENERATING;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Long getSalesAnalysisId() {
        return salesAnalysisId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public SolutionBundleStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
