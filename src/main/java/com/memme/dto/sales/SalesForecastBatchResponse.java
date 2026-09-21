package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SalesForecastBatchResponse(
        List<Prediction> predictions
) {

    public record Prediction(
            Long storeId,
            LocalDate targetDate,
            LocalDate basisDate,
            BigDecimal predictedSalesAmount,
            String modelVersion,
            OffsetDateTime generatedAt
    ) {}
}
