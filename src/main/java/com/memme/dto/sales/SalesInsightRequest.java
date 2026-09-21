package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public record SalesInsightRequest(
        Long storeId,
        Long salesAnalysisId,
        YearMonth targetMonth,
        String triggerType,
        Metrics metrics,
        int maxInsightCount
) {

    public record Metrics(
            BigDecimal totalSales,
            long orderCount,
            BigDecimal averageOrderValue,
            List<Map<String, Object>> salesTrend,
            List<Map<String, Object>> weekdaySales,
            List<Map<String, Object>> hourlySales
    ) {}
}
