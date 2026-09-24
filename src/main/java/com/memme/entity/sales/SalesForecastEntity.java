package com.memme.entity.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sales_forecasts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_forecasts_store_target_date",
                columnNames = {"store_id", "target_date"}
        )
)
public class SalesForecastEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "based_on_upload_id", nullable = false)
    private Long basedOnUploadId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "basis_date", nullable = false)
    private LocalDate basisDate;

    @Column(name = "predicted_sales_amount", nullable = false)
    private long predictedSalesAmount;

    @Column(name = "lower_bound", nullable = false)
    private long lowerBound;

    @Column(name = "upper_bound", nullable = false)
    private long upperBound;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    protected SalesForecastEntity() {
    }

    private SalesForecastEntity(
            Long storeId,
            Long basedOnUploadId,
            LocalDate targetDate,
            LocalDate basisDate,
            long predictedSalesAmount,
            long lowerBound,
            long upperBound,
            String modelVersion,
            LocalDateTime generatedAt
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.basedOnUploadId = Objects.requireNonNull(basedOnUploadId, "basedOnUploadId");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate");
        this.basisDate = Objects.requireNonNull(basisDate, "basisDate");
        validateAmounts(predictedSalesAmount, lowerBound, upperBound);
        this.predictedSalesAmount = predictedSalesAmount;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.modelVersion = validateModelVersion(modelVersion);
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
    }

    public static SalesForecastEntity create(
            Long storeId,
            Long basedOnUploadId,
            LocalDate targetDate,
            LocalDate basisDate,
            long predictedSalesAmount,
            long lowerBound,
            long upperBound,
            String modelVersion,
            LocalDateTime generatedAt
    ) {
        return new SalesForecastEntity(
                storeId,
                basedOnUploadId,
                targetDate,
                basisDate,
                predictedSalesAmount,
                lowerBound,
                upperBound,
                modelVersion,
                generatedAt
        );
    }

    private static void validateAmounts(long predictedSalesAmount, long lowerBound, long upperBound) {
        if (predictedSalesAmount < 0 || lowerBound < 0 || upperBound < 0) {
            throw new IllegalArgumentException("forecast amounts must not be negative");
        }
        if (lowerBound > predictedSalesAmount || predictedSalesAmount > upperBound) {
            throw new IllegalArgumentException(
                    "predictedSalesAmount must be between lowerBound and upperBound"
            );
        }
    }

    private static String validateModelVersion(String modelVersion) {
        if (modelVersion == null || modelVersion.isBlank() || modelVersion.length() > 50) {
            throw new IllegalArgumentException("modelVersion must be between 1 and 50 characters");
        }
        return modelVersion;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getBasedOnUploadId() { return basedOnUploadId; }
    public LocalDate getTargetDate() { return targetDate; }
    public LocalDate getBasisDate() { return basisDate; }
    public long getPredictedSalesAmount() { return predictedSalesAmount; }
    public long getLowerBound() { return lowerBound; }
    public long getUpperBound() { return upperBound; }
    public String getModelVersion() { return modelVersion; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}
