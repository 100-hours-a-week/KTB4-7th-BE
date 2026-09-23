package com.memme.dto.sales;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SalesSummaryMetric(
        @NotNull @PositiveOrZero BigDecimal totalSales,
        @NotNull @PositiveOrZero BigDecimal menuSales,
        @NotNull @PositiveOrZero Long orderCount,
        @NotNull @PositiveOrZero BigDecimal averageOrderValue,
        @DecimalMin("-1.0") BigDecimal vsPrevPeriod
) {}
