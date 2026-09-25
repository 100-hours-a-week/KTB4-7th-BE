package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

public record SalesAnalysisResponse(
        SalesPeriod period,
        DateRange comparisonPeriod,
        Kpis kpis,
        List<DailySales> dailySales,
        List<MenuRanking> menuRankings,
        List<HourlySales> hourlySales,
        List<WeekdaySales> weekdaySales,
        AiInsight aiInsight
) {

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public record Kpis(
            BigDecimal totalSales,
            long orderCount,
            BigDecimal averageOrderValue,
            Changes changes
    ) {}

    public record Changes(
            BigDecimal totalSalesRate,
            BigDecimal orderCountRate,
            BigDecimal averageOrderValueRate
    ) {}

    public record DailySales(LocalDate date, BigDecimal salesAmount) {}

    public record MenuRanking(int rank, String menuName, BigDecimal salesAmount, long quantity) {}

    public record HourlySales(int hour, BigDecimal salesAmount) {}

    public record WeekdaySales(DayOfWeek dayOfWeek, BigDecimal salesAmount) {}

    public record AiInsight(
            YearMonth targetMonth,
            List<String> insights,
            OffsetDateTime generatedAt
    ) {
        public AiInsight {
            insights = List.copyOf(insights);
        }
    }

    public record InsufficientData(AiInsight aiInsight) {}

    public record EmptyData(
            SalesPeriod period,
            Kpis kpis,
            List<DailySales> dailySales,
            List<MenuRanking> menuRankings,
            List<HourlySales> hourlySales,
            List<WeekdaySales> weekdaySales,
            AiInsight aiInsight
    ) {}
}
