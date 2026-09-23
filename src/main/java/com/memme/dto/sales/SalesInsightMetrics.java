package com.memme.dto.sales;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SalesInsightMetrics(
        @NotNull @Valid SalesSummaryMetric salesSummary,
        @NotNull @Valid List<SalesTrendMetric> salesTrend,
        @NotNull @Valid List<WeekdaySalesMetric> weekdaySales,
        @NotNull @Valid List<HourlySalesMetric> hourlySales,
        @NotNull @Valid List<CategorySalesMetric> categorySales,
        @NotNull @Valid List<MenuRankingMetric> menuRankings
) {
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
