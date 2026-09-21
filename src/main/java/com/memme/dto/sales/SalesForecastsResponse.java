package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SalesForecastsResponse(
        List<Forecast> forecasts,
        OffsetDateTime generatedAt
) {

    public record Forecast(
            LocalDate targetDate,
            BigDecimal predictedSalesAmount,
            BigDecimal lowerBound,
            BigDecimal upperBound
    ) {}
}
