package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitAnalysisResponse(
        Summary summary,
        List<DailyProfit> dailyProfits,
        AiInsight aiInsight
) {
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

    public record DailyProfit(LocalDate date, long netProfit) {
    }

    public record AiInsight(String status, List<String> insights) {
    }
}
