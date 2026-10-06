package com.memme.service.sales.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfitCalculationServiceTest {

    private final ProfitCalculationService service = new ProfitCalculationService();

    @Test
    void allocatesFixedCostsAcrossTwoMonthsAndUsesEachMonthsIngredientRate() {
        ProfitCalculationResult result = service.calculate(
                LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 4, 10),
                List.of(
                        sales(2026, 3, 16, 4_000_000),
                        sales(2026, 4, 10, 2_000_000)
                ),
                List.of(
                        cost(2026, 3, 3_100_000, 0, "0.3"),
                        cost(2026, 4, 0, 3_000_000, "0.25")
                )
        );

        assertThat(result.status()).isEqualTo(ProfitCalculationResult.Status.COMPLETED);
        assertThat(result.amounts()).isEqualTo(new ProfitCalculationResult.Amounts(
                6_000_000, 1_700_000, 2_600_000, 1_700_000
        ));
    }

    @Test
    void roundsMonthlyIngredientTotalOnceAndPreservesNegativeCancellation() {
        ProfitCalculationResult positive = service.calculate(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(sales(2026, 3, 1, 1), sales(2026, 3, 2, 1)),
                List.of(cost(2026, 3, 0, 0, "0.25"))
        );
        ProfitCalculationResult negative = service.calculate(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(sales(2026, 3, 1, -1)),
                List.of(cost(2026, 3, 0, 0, "0.5"))
        );

        assertThat(positive.amounts()).isEqualTo(new ProfitCalculationResult.Amounts(2, 1, 0, 1));
        assertThat(negative.amounts()).isEqualTo(new ProfitCalculationResult.Amounts(-1, -1, 0, 0));
    }

    @Test
    void proratesLeapYearFebruaryAndKeepsZeroCostAsEntered() {
        ProfitCalculationResult result = service.calculate(
                LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29),
                List.of(sales(2028, 2, 29, 0)),
                List.of(cost(2028, 2, 290_000, 0, "0"))
        );

        assertThat(result.amounts()).isEqualTo(new ProfitCalculationResult.Amounts(0, 0, 10_000, -10_000));
    }

    @Test
    void reportsMissingCostsBeforeEmptySalesAndDistinguishesZeroSales() {
        LocalDate start = LocalDate.of(2026, 3, 31);
        LocalDate end = LocalDate.of(2026, 4, 1);
        MonthlyProfitCost march = cost(2026, 3, 0, 0, "0");
        MonthlyProfitCost april = cost(2026, 4, 0, 0, "0");

        ProfitCalculationResult missing = service.calculate(start, end, List.of(), List.of(march));
        ProfitCalculationResult empty = service.calculate(start, end, List.of(), List.of(march, april));
        ProfitCalculationResult zeroSales = service.calculate(
                start, end, List.of(sales(2026, 3, 31, 0)), List.of(march, april)
        );

        assertThat(missing.status()).isEqualTo(ProfitCalculationResult.Status.COST_INPUT_REQUIRED);
        assertThat(missing.missingCostMonths()).containsExactly(YearMonth.of(2026, 4));
        assertThat(empty.status()).isEqualTo(ProfitCalculationResult.Status.EMPTY);
        assertThat(zeroSales.status()).isEqualTo(ProfitCalculationResult.Status.COMPLETED);
        assertThat(zeroSales.amounts().netProfit()).isZero();
    }

    @Test
    void skippedCostInputDoesNotFabricateZeroCostProfit() {
        ProfitCalculationResult result = service.calculate(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1),
                List.of(sales(2026, 10, 1, 100_000)),
                List.of()
        );

        assertThat(result.status()).isEqualTo(ProfitCalculationResult.Status.COST_INPUT_REQUIRED);
        assertThat(result.missingCostMonths()).containsExactly(YearMonth.of(2026, 10));
        assertThat(result.amounts()).isNull();
    }

    @Test
    void rejectsInvalidCostInputsWithoutSilentlyRoundingRate() {
        assertThatThrownBy(() -> cost(2026, 3, -1, 0, "0.2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cost(2026, 3, 0, -1, "0.2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cost(2026, 3, 0, 0, "1.0001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cost(2026, 3, 0, 0, "0.12345"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cost(2026, 3, 0, 0, "-0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateMonthlyCostsAndSalesOutsidePeriod() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        MonthlyProfitCost march = cost(2026, 3, 0, 0, "0");

        assertThatThrownBy(() -> service.calculate(date, date, List.of(sales(2026, 3, 1, 1)),
                List.of(march, march))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(date, date, List.of(sales(2026, 3, 2, 1)),
                List.of(march))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 일별_순이익은_매출_없는_날도_포함하고_합계가_기간_순이익과_일치한다() {
        ProfitCalculationResult result = service.calculate(
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 2),
                List.of(sales(2026, 3, 31, 1), sales(2026, 4, 2, -1)),
                List.of(cost(2026, 3, 31, 0, "0.5"), cost(2026, 4, 30, 0, "0.5"))
        );

        assertThat(result.dailyProfits()).extracting(ProfitCalculationResult.DailyProfit::date)
                .containsExactly(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 2));
        assertThat(result.dailyProfits().stream().mapToLong(ProfitCalculationResult.DailyProfit::netProfit).sum())
                .isEqualTo(result.amounts().netProfit());
    }

    @Test
    void 월별_반올림_잔여분은_일별_배분으로_합산한다() {
        ProfitCalculationResult result = service.calculate(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3),
                List.of(sales(2026, 3, 1, 1), sales(2026, 3, 2, 1), sales(2026, 3, 3, 1)),
                List.of(cost(2026, 3, 31, 0, "0.5"))
        );

        assertThat(result.amounts()).isEqualTo(new ProfitCalculationResult.Amounts(3, 2, 3, -2));
        assertThat(result.dailyProfits()).extracting(ProfitCalculationResult.DailyProfit::netProfit)
                .containsExactly(-1L, 0L, -1L);
    }

    private static MonthlyProfitCost cost(int year, int month, long rent, long labor, String rate) {
        return new MonthlyProfitCost(YearMonth.of(year, month), rent, labor, new BigDecimal(rate));
    }

    private static DailyProfitSales sales(int year, int month, int day, long amount) {
        return new DailyProfitSales(LocalDate.of(year, month, day), amount);
    }
}
