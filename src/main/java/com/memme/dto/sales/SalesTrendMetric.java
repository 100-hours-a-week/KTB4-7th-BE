package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SalesTrendMetric(
        @NotNull LocalDate date,
        @NotNull @PositiveOrZero BigDecimal menuSales,
        @NotNull @PositiveOrZero Long orderCount
) {}
