package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.DayOfWeek;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record WeekdaySalesMetric(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @PositiveOrZero BigDecimal menuSales,
        @NotNull @PositiveOrZero Long orderCount
) {}
