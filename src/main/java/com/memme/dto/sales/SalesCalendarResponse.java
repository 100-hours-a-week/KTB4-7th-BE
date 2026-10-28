package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesCalendarResponse(
        String month,
        String timezone,
        AvailableRange availableRange,
        LocalDate selectedDate,
        MonthSummary monthSummary,
        List<Day> days,
        SelectedDay selectedDay
) {

    public SalesCalendarResponse {
        days = List.copyOf(days);
    }

    public record AvailableRange(String firstMonth, String lastMonth) {
    }

    public record MonthSummary(
            long totalSalesAmount,
            long totalOrderCount,
            Long averageDailySalesAmount,
            int dataDayCount
    ) {
    }

    public record Day(
            LocalDate date,
            String dayOfWeek,
            boolean isToday,
            boolean isSelected,
            String dataStatus,
            Long salesAmount,
            Integer orderCount,
            String salesLevel,
            Weather weather
    ) {
    }

    public record Weather(String condition) {
    }

    public record SelectedDay(
            LocalDate date,
            String dataStatus,
            Long salesAmount,
            Integer orderCount,
            Long averageOrderAmount,
            Insight insight
    ) {
    }

    public record Insight(
            String salesLevel,
            Long monthlyAverageSalesAmount,
            BigDecimal deviationRate
    ) {
    }
}
