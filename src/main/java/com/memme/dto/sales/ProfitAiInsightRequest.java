package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitAiInsightRequest(
        Long storeId,
        Period period,
        Summary summary,
        List<ProfitAnalysisResponse.DailyProfit> dailyProfits,
        int maxInsightCount
) {
    public record Period(LocalDate startDate, LocalDate endDate) {
    }

    public record Summary(
            long totalNetAmount,
            long ingredientCost,
            long fixedCost,
            long netProfit,
            BigDecimal netProfitRate,
            Long previousNetProfit,
            BigDecimal netProfitChangeRate
    ) {
    }
}
