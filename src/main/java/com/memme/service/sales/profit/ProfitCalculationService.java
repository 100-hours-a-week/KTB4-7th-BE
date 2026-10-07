package com.memme.service.sales.profit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    public ProfitCalculationResult calculate(
            LocalDate startDate,
            LocalDate endDate,
            List<DailyProfitSales> dailySales,
            List<MonthlyProfitCost> monthlyCosts
    ) {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(dailySales, "dailySales");
        Objects.requireNonNull(monthlyCosts, "monthlyCosts");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        Map<YearMonth, MonthlyProfitCost> costsByMonth = new HashMap<>();
        for (MonthlyProfitCost cost : monthlyCosts) {
            Objects.requireNonNull(cost, "monthlyCosts item");
            if (costsByMonth.putIfAbsent(cost.month(), cost) != null) {
                throw new IllegalArgumentException("같은 월의 비용을 중복 입력할 수 없습니다: " + cost.month());
            }
        }

        List<YearMonth> missingMonths = new ArrayList<>();
        for (YearMonth month = YearMonth.from(startDate);
                !month.isAfter(YearMonth.from(endDate));
                month = month.plusMonths(1)) {
            if (!costsByMonth.containsKey(month)) {
                missingMonths.add(month);
            }
        }
        if (!missingMonths.isEmpty()) {
            return ProfitCalculationResult.costInputRequired(missingMonths);
        }

        if (dailySales.isEmpty()) {
            return ProfitCalculationResult.empty();
        }

        Map<YearMonth, BigDecimal> salesByMonth = new HashMap<>();
        Map<LocalDate, BigDecimal> salesByDate = new HashMap<>();
        for (DailyProfitSales day : dailySales) {
            Objects.requireNonNull(day, "dailySales item");
            if (day.salesDate().isBefore(startDate) || day.salesDate().isAfter(endDate)) {
                throw new IllegalArgumentException("조회 기간 밖의 매출이 포함됐습니다: " + day.salesDate());
            }
            salesByMonth.merge(
                    YearMonth.from(day.salesDate()),
                    BigDecimal.valueOf(day.totalNetAmount()),
                    BigDecimal::add
            );
            salesByDate.merge(day.salesDate(), BigDecimal.valueOf(day.totalNetAmount()), BigDecimal::add);
        }

        BigDecimal totalNetAmount = BigDecimal.ZERO;
        BigDecimal ingredientCost = BigDecimal.ZERO;
        BigDecimal fixedCost = BigDecimal.ZERO;
        List<ProfitCalculationResult.DailyProfit> dailyProfits = new ArrayList<>();
        for (YearMonth month = YearMonth.from(startDate);
                !month.isAfter(YearMonth.from(endDate));
                month = month.plusMonths(1)) {
            MonthlyProfitCost cost = costsByMonth.get(month);
            BigDecimal monthlySales = salesByMonth.getOrDefault(month, BigDecimal.ZERO);
            LocalDate overlapStart = startDate.isAfter(month.atDay(1)) ? startDate : month.atDay(1);
            LocalDate overlapEnd = endDate.isBefore(month.atEndOfMonth()) ? endDate : month.atEndOfMonth();
            long overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;

            totalNetAmount = totalNetAmount.add(monthlySales);
            ingredientCost = ingredientCost.add(
                    monthlySales.multiply(cost.ingredientCostRate()).setScale(0, RoundingMode.HALF_UP)
            );
            BigDecimal monthlyFixedCost = BigDecimal.valueOf(cost.rentAmount())
                    .add(BigDecimal.valueOf(cost.laborAmount()));
            fixedCost = fixedCost.add(monthlyFixedCost.multiply(BigDecimal.valueOf(overlapDays))
                    .divide(BigDecimal.valueOf(month.lengthOfMonth()), 0, RoundingMode.HALF_UP));

            BigDecimal cumulativeSales = BigDecimal.ZERO;
            BigDecimal previousIngredientCost = BigDecimal.ZERO;
            BigDecimal previousFixedCost = BigDecimal.ZERO;
            long elapsedDays = 0;
            for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
                BigDecimal daySales = salesByDate.getOrDefault(date, BigDecimal.ZERO);
                cumulativeSales = cumulativeSales.add(daySales);
                elapsedDays++;
                BigDecimal cumulativeIngredientCost = cumulativeSales.multiply(cost.ingredientCostRate())
                        .setScale(0, RoundingMode.HALF_UP);
                BigDecimal cumulativeFixedCost = monthlyFixedCost.multiply(BigDecimal.valueOf(elapsedDays))
                        .divide(BigDecimal.valueOf(month.lengthOfMonth()), 0, RoundingMode.HALF_UP);
                BigDecimal dayProfit = daySales
                        .subtract(cumulativeIngredientCost.subtract(previousIngredientCost))
                        .subtract(cumulativeFixedCost.subtract(previousFixedCost));
                dailyProfits.add(new ProfitCalculationResult.DailyProfit(date, dayProfit.longValueExact()));
                previousIngredientCost = cumulativeIngredientCost;
                previousFixedCost = cumulativeFixedCost;
            }
        }

        BigDecimal netProfit = totalNetAmount.subtract(ingredientCost).subtract(fixedCost);
        return ProfitCalculationResult.completed(new ProfitCalculationResult.Amounts(
                totalNetAmount.longValueExact(),
                ingredientCost.longValueExact(),
                fixedCost.longValueExact(),
                netProfit.longValueExact()
        ), dailyProfits);
    }
}
