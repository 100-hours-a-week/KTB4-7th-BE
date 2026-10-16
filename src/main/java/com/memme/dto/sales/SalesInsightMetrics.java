package com.memme.dto.sales;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SalesInsightMetrics(
        @NotNull @Valid SalesSummaryMetric salesSummary,
        @NotNull @Valid List<SalesTrendMetric> salesTrend,
        @NotNull @Valid List<WeekdaySalesMetric> weekdaySales,
        @NotNull @Valid List<HourlySalesMetric> hourlySales,
        @NotNull @Valid List<CategorySalesMetric> categorySales,
        @NotNull @Valid List<MenuRankingMetric> menuRankings,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid DistrictBenchmarkMetric districtBenchmark
) {
    public SalesInsightMetrics(
            SalesSummaryMetric salesSummary,
            List<SalesTrendMetric> salesTrend,
            List<WeekdaySalesMetric> weekdaySales,
            List<HourlySalesMetric> hourlySales,
            List<CategorySalesMetric> categorySales,
            List<MenuRankingMetric> menuRankings
    ) {
        this(salesSummary, salesTrend, weekdaySales, hourlySales, categorySales, menuRankings, null);
    }

    public SalesInsightMetrics {
        salesTrend = copyOfNullable(salesTrend);
        weekdaySales = copyOfNullable(weekdaySales);
        hourlySales = copyOfNullable(hourlySales);
        categorySales = copyOfNullable(categorySales);
        menuRankings = copyOfNullable(menuRankings);
    }

    private static <T> List<T> copyOfNullable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
