package com.memme.service.sales.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import com.memme.entity.sales.SalesForecastEntity;

public record SalesForecastUpsertCommand(
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

    public SalesForecastUpsertCommand {
        Objects.requireNonNull(storeId, "storeId");
        Objects.requireNonNull(basedOnUploadId, "basedOnUploadId");
        Objects.requireNonNull(targetDate, "targetDate");
        Objects.requireNonNull(basisDate, "basisDate");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
        SalesForecastEntity.create(
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
}
