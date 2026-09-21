package com.memme.entity.sales;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sales_analyses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_analysis_run",
                columnNames = "analysis_run_id"
        )
)
public class SalesAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_run_id", nullable = false)
    private Long analysisRunId;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SalesAnalysisEntity() {
    }

    private SalesAnalysisEntity(Long analysisRunId, String summaryText) {
        this.analysisRunId = Objects.requireNonNull(analysisRunId, "analysisRunId");
        this.summaryText = summaryText;
    }

    public static SalesAnalysisEntity create(Long analysisRunId, String summaryText) {
        return new SalesAnalysisEntity(analysisRunId, summaryText);
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

    public Long getAnalysisRunId() {
        return analysisRunId;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
