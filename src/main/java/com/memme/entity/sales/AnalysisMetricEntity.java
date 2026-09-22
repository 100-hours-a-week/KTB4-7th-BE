package com.memme.entity.sales;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "analysis_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_metric_code",
                columnNames = {"sales_analysis_id", "metric_code"}
        ),
        indexes = @Index(name = "idx_analysis_metrics_code", columnList = "metric_code")
)
public class AnalysisMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_analysis_id", nullable = false)
    private Long salesAnalysisId;

    @Column(name = "metric_code", nullable = false, length = 50)
    private String metricCode;

    @Column(name = "metric_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "comparison_value", precision = 18, scale = 4)
    private BigDecimal comparisonValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    private AnalysisMetricUnit unit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AnalysisMetricEntity() {
    }

    private AnalysisMetricEntity(
            Long salesAnalysisId,
            String metricCode,
            BigDecimal metricValue,
            BigDecimal comparisonValue,
            AnalysisMetricUnit unit
    ) {
        this.salesAnalysisId = Objects.requireNonNull(salesAnalysisId, "salesAnalysisId");
        this.metricCode = requireText(metricCode, "metricCode");
        this.metricValue = Objects.requireNonNull(metricValue, "metricValue");
        this.comparisonValue = comparisonValue;
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    public static AnalysisMetricEntity create(
            Long salesAnalysisId,
            String metricCode,
            BigDecimal metricValue,
            BigDecimal comparisonValue,
            AnalysisMetricUnit unit
    ) {
        return new AnalysisMetricEntity(
                salesAnalysisId,
                metricCode,
                metricValue,
                comparisonValue,
                unit
        );
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
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

    public Long getSalesAnalysisId() {
        return salesAnalysisId;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public BigDecimal getComparisonValue() {
        return comparisonValue;
    }

    public AnalysisMetricUnit getUnit() {
        return unit;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
