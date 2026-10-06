package com.memme.service.sales.profit;

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.List;

public record ProfitCalculationResult(
        Status status,
        Amounts amounts,
        List<YearMonth> missingCostMonths,
        List<DailyProfit> dailyProfits
) {

    public enum Status {
        COMPLETED,
        COST_INPUT_REQUIRED,
        EMPTY
    }

    public record Amounts(long totalNetAmount, long ingredientCost, long fixedCost, long netProfit) {
    }

    public record DailyProfit(LocalDate date, long netProfit) {
    }

    public ProfitCalculationResult {
        missingCostMonths = List.copyOf(missingCostMonths);
        dailyProfits = List.copyOf(dailyProfits);
    }

    public static ProfitCalculationResult completed(Amounts amounts, List<DailyProfit> dailyProfits) {
        return new ProfitCalculationResult(Status.COMPLETED, amounts, List.of(), dailyProfits);
    }

    public static ProfitCalculationResult costInputRequired(List<YearMonth> months) {
        return new ProfitCalculationResult(Status.COST_INPUT_REQUIRED, null, months, List.of());
    }

    public static ProfitCalculationResult empty() {
        return new ProfitCalculationResult(Status.EMPTY, null, List.of(), List.of());
    }
}
