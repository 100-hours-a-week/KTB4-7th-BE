package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record SalesAnalysisDetailResponse(
        YearMonth period,
        Kpis kpis,
        List<DailySales> dailySales,
        List<MenuRanking> menuRankings
) {

    public record Kpis(BigDecimal netSales, long orderCount, BigDecimal averageOrderValue) {}

    public record DailySales(LocalDate date, BigDecimal netSales) {}

    public record MenuRanking(String menuName, BigDecimal netSales, long quantity) {}
}
