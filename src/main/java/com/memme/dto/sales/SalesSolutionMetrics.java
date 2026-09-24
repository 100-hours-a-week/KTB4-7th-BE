package com.memme.dto.sales;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SalesSolutionMetrics(
        @NotNull @Valid SalesSummary salesSummary,
        @NotNull @Valid List<HourlyPoint> hourlyProfile,
        @NotNull @Valid List<CategoryPoint> categoryBreakdown,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long predictedSalesToday,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> reviewSummary
) {

    public record SalesSummary(long netSales, Double vsPrevPeriod) {}

    public record HourlyPoint(
            @NotBlank String dayType,
            @Min(0) @Max(23) int hour,
            long amount
    ) {}

    public record CategoryPoint(
            @NotBlank String name,
            double share,
            Double vsPrevPeriod
    ) {}
}
