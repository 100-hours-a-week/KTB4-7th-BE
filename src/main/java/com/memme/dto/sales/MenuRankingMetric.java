package com.memme.dto.sales;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record MenuRankingMetric(
        @Positive int rank,
        @NotBlank String menuName,
        @NotNull @PositiveOrZero BigDecimal menuSales,
        @NotNull @PositiveOrZero Long quantity,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal ratio,
        @DecimalMin("-1.0") BigDecimal vsPrevPeriod,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal previousRatio
) {
    public MenuRankingMetric(int rank, String menuName, BigDecimal menuSales, Long quantity,
                             BigDecimal ratio, BigDecimal vsPrevPeriod) {
        this(rank, menuName, menuSales, quantity, ratio, vsPrevPeriod, null);
    }
}
