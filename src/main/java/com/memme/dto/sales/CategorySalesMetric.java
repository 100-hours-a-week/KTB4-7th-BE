package com.memme.dto.sales;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CategorySalesMetric(
        @NotBlank String categoryName,
        @NotNull @PositiveOrZero BigDecimal menuSales,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal ratio,
        @DecimalMin("-1.0") BigDecimal vsPrevPeriod
) {}
