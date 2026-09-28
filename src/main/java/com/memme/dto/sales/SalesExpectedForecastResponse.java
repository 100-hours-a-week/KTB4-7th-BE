package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record SalesExpectedForecastResponse(
        YearMonth targetMonth,
        BigDecimal actualSalesAmount,
        BigDecimal forecastSalesAmount,
        BigDecimal expectedSalesAmount,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        List<DailyForecast> dailyForecasts
) {

    public record DailyForecast(
            LocalDate targetDate,
            BigDecimal predictedSalesAmount,
            BigDecimal lowerBound,
            BigDecimal upperBound
    ) {}
}
