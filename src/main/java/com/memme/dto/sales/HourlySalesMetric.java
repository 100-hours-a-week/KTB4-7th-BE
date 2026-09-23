package com.memme.dto.sales;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record HourlySalesMetric(
        @NotNull SalesInsightDayType dayType,
        @Min(0) @Max(23) int hour,
        @NotNull @PositiveOrZero BigDecimal menuSales,
        @NotNull @PositiveOrZero Long orderCount
) {}
