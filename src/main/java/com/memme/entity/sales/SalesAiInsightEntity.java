package com.memme.entity.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "sales_ai_insights",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_ai_insights_store_month",
                columnNames = {"store_id", "target_month"}
        )
)
public class SalesAiInsightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "sales_analysis_id", nullable = false)
    private Long salesAnalysisId;

    @Column(name = "target_month", nullable = false)
    private LocalDate targetMonth;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "insights", columnDefinition = "json")
    private List<String> insights;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SalesAiInsightStatus status;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SalesAiInsightEntity() {
    }

    private SalesAiInsightEntity(Long storeId, Long salesAnalysisId, YearMonth targetMonth) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.salesAnalysisId = Objects.requireNonNull(salesAnalysisId, "salesAnalysisId");
        this.targetMonth = Objects.requireNonNull(targetMonth, "targetMonth").atDay(1);
        this.status = SalesAiInsightStatus.PENDING;
    }

    public static SalesAiInsightEntity pending(
            Long storeId,
            Long salesAnalysisId,
            YearMonth targetMonth
    ) {
        return new SalesAiInsightEntity(storeId, salesAnalysisId, targetMonth);
    }

    public void restart(Long newSalesAnalysisId) {
        this.salesAnalysisId = Objects.requireNonNull(newSalesAnalysisId, "salesAnalysisId");
        this.insights = null;
        this.generatedAt = null;
        this.status = SalesAiInsightStatus.PENDING;
    }

    public void startGenerating() {
        requireStatus(SalesAiInsightStatus.PENDING);
        status = SalesAiInsightStatus.GENERATING;
    }

    public void complete(List<String> generatedInsights) {
        requireStatus(SalesAiInsightStatus.GENERATING);
        if (generatedInsights == null || generatedInsights.isEmpty()) {
            throw new IllegalArgumentException("generatedInsights must not be empty");
        }
        insights = List.copyOf(generatedInsights);
        generatedAt = LocalDateTime.now();
        status = SalesAiInsightStatus.COMPLETED;
    }

    public void markInsufficientData() {
        requireStatus(SalesAiInsightStatus.GENERATING);
        insights = null;
        generatedAt = LocalDateTime.now();
        status = SalesAiInsightStatus.INSUFFICIENT_DATA;
    }

    public void fail() {
        if (status == SalesAiInsightStatus.COMPLETED) {
            throw new IllegalStateException("completed insight must not be failed");
        }
        insights = null;
        generatedAt = LocalDateTime.now();
        status = SalesAiInsightStatus.FAILED;
    }

    private void requireStatus(SalesAiInsightStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("insight status must be " + expected);
        }
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

    public YearMonth getTargetMonth() {
        return YearMonth.from(targetMonth);
    }

    public List<String> getInsights() {
        return insights == null ? null : List.copyOf(insights);
    }

    public SalesAiInsightStatus getStatus() {
        return status;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
}
