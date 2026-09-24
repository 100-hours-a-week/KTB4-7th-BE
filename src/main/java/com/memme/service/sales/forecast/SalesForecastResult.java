package com.memme.service.sales.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.memme.entity.sales.SalesForecastEntity;

public record SalesForecastResult(
        Long id,
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

    static SalesForecastResult from(SalesForecastEntity entity) {
        return new SalesForecastResult(
                entity.getId(),
                entity.getStoreId(),
                entity.getBasedOnUploadId(),
                entity.getTargetDate(),
                entity.getBasisDate(),
                entity.getPredictedSalesAmount(),
                entity.getLowerBound(),
                entity.getUpperBound(),
                entity.getModelVersion(),
                entity.getGeneratedAt()
        );
    }
}
