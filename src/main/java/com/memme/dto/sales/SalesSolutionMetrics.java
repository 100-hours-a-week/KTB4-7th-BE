package com.memme.dto.sales;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SalesSolutionMetrics(
        @NotNull @Valid SalesSummary salesSummary,
        @NotNull @Valid List<HourlyPoint> hourlyProfile,
        @NotNull @Valid List<CategoryPoint> categoryBreakdown,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long predictedSalesToday,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> reviewSummary,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid WeatherContext weatherContext
) {

    public SalesSolutionMetrics(
            SalesSummary salesSummary,
            List<HourlyPoint> hourlyProfile,
            List<CategoryPoint> categoryBreakdown,
            Long predictedSalesToday,
            Map<String, Object> reviewSummary
    ) {
        this(salesSummary, hourlyProfile, categoryBreakdown, predictedSalesToday, reviewSummary, null);
    }

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

    public record WeatherContext(
            @NotBlank String basisDateTime,
            @NotBlank String skyText,
            int minTempC,
            int maxTempC,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double maxPop,
            @NotNull List<@NotNull @Min(0) @Max(23) Integer> rainHours,
            @JsonInclude(JsonInclude.Include.NON_NULL) @DecimalMin("0.0") Double precipitationMm,
            @NotNull Boolean isNotable
    ) {}
}
