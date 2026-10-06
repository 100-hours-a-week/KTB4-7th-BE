package com.memme.service.sales.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.sales.SalesPeriod;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.store.StoreCostItem;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreCostItemRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.analysis.SalesAnalysisQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfitAnalysisQueryServiceTest {

    @Mock private SalesAnalysisQueryService periodService;
    @Mock private SalesDailySummaryRepository dailyRepository;
    @Mock private StoreCostItemRepository costRepository;
    @Mock private StoreOwnershipRepository ownershipRepository;
    private ProfitAnalysisQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProfitAnalysisQueryService(periodService, dailyRepository, costRepository,
                ownershipRepository, new ProfitCalculationService(), new ProfitInsightService());
    }

    @Test
    void 순이익과_전기_증감률을_계산하고_일별_합계를_맞춘다() {
        LocalDate current = LocalDate.of(2026, 10, 10);
        LocalDate previous = current.minusDays(1);
        stubPeriod(current, current);
        stubSales(List.of(sales(current, 1_000), sales(previous, 500)));
        stubCosts(List.of(cost(LocalDate.of(2026, 10, 1), 31, "0.4")));

        var result = (ProfitAnalysisQueryResult.Completed) service.query(1L, 20L, "CUSTOM",
                "2026-10-10", "2026-10-10");
        var summary = result.data().summary();

        assertThat(summary.totalNetAmount()).isEqualTo(1_000);
        assertThat(summary.ingredientCost()).isEqualTo(400);
        assertThat(summary.fixedCost()).isEqualTo(1);
        assertThat(summary.netProfit()).isEqualTo(599);
        assertThat(summary.previousNetProfit()).isEqualTo(299);
        assertThat(summary.netProfitRate()).isEqualByComparingTo("0.5990");
        assertThat(summary.netProfitChangeRate()).isEqualByComparingTo("1.0033");
        assertThat(result.data().dailyProfits()).hasSize(1);
        assertThat(result.data().dailyProfits().getFirst().netProfit()).isEqualTo(summary.netProfit());
        assertThat(result.data().aiInsight().status()).isEqualTo("COMPLETED");
    }

    @Test
    void 현재_비용이_없으면_필요한_월을_반환한다() {
        LocalDate date = LocalDate.of(2026, 9, 30);
        stubPeriod(date, date.plusDays(1));
        stubSales(List.of(sales(date, 100)));
        stubCosts(List.of(cost(LocalDate.of(2026, 9, 1), 0, "0")));

        var result = (ProfitAnalysisQueryResult.CostInputRequired) service.query(1L, 20L,
                "CUSTOM", "2026-09-30", "2026-10-01");

        assertThat(result.data().missingCostMonths()).containsExactly("2026-10");
    }

    @Test
    void 전기_비용만_없으면_당기_순이익은_유지하고_비교값은_null이다() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        stubPeriod(date, date);
        stubSales(List.of(sales(date, 100), sales(date.minusDays(1), 200)));
        stubCosts(List.of(cost(date, 0, "0")));

        var result = (ProfitAnalysisQueryResult.Completed) service.query(1L, 20L,
                "CUSTOM", "2026-09-01", "2026-09-01");

        assertThat(result.data().summary().netProfit()).isEqualTo(100);
        assertThat(result.data().summary().previousNetProfit()).isNull();
        assertThat(result.data().summary().netProfitChangeRate()).isNull();
    }

    @Test
    void 전기_순이익이_0원이면_증감률은_null이다() {
        LocalDate current = LocalDate.of(2026, 10, 10);
        stubPeriod(current, current);
        stubSales(List.of(sales(current, 100), sales(current.minusDays(1), 0)));
        stubCosts(List.of(cost(LocalDate.of(2026, 10, 1), 0, "0")));

        var result = (ProfitAnalysisQueryResult.Completed) service.query(1L, 20L,
                "CUSTOM", "2026-10-10", "2026-10-10");

        assertThat(result.data().summary().previousNetProfit()).isZero();
        assertThat(result.data().summary().netProfitChangeRate()).isNull();
    }

    @Test
    void 인사이트_생성에_실패해도_계산된_순이익을_반환한다() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        stubPeriod(date, date);
        stubSales(List.of(sales(date, 100)));
        stubCosts(List.of(cost(date, 0, "0")));
        ProfitInsightService failingInsight = mock(ProfitInsightService.class);
        when(failingInsight.describe(eq(100L), eq(null), anyList()))
                .thenThrow(new IllegalStateException("insight unavailable"));
        service = new ProfitAnalysisQueryService(periodService, dailyRepository, costRepository,
                ownershipRepository, new ProfitCalculationService(), failingInsight);

        var result = (ProfitAnalysisQueryResult.Completed) service.query(1L, 20L,
                "CUSTOM", "2026-09-01", "2026-09-01");

        assertThat(result.data().summary().netProfit()).isEqualTo(100);
        assertThat(result.data().aiInsight().status()).isEqualTo("FAILED");
        assertThat(result.data().aiInsight().insights()).isEmpty();
    }

    @Test
    void 매출_집계_행이_없으면_EMPTY다() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        stubPeriod(date, date);
        stubSales(List.of());
        stubCosts(List.of(cost(LocalDate.of(2026, 10, 1), 0, "0")));

        assertThat(service.query(1L, 20L, "CUSTOM", "2026-10-10", "2026-10-10"))
                .isInstanceOf(ProfitAnalysisQueryResult.Empty.class);
    }

    @Test
    void 다른_매장_비용과_매출은_조회할_수_없다() {
        assertThatThrownBy(() -> service.query(1L, 20L, "TODAY", null, null))
                .isInstanceOf(SalesAnalysisRequestException.class);
    }

    private void stubPeriod(LocalDate start, LocalDate end) {
        when(ownershipRepository.existsActiveStoreOwnedBy(20L, 1L)).thenReturn(true);
        when(periodService.resolvePeriod(eq("CUSTOM"), any(), any()))
                .thenReturn(new SalesPeriod("CUSTOM", start, end));
    }

    private void stubSales(List<SalesDailySummaryEntity> rows) {
        when(dailyRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(eq(20L), any(), any()))
                .thenAnswer(call -> {
                    LocalDate start = call.getArgument(1);
                    LocalDate end = call.getArgument(2);
                    return rows.stream().filter(row -> !row.getSalesDate().isBefore(start)
                            && !row.getSalesDate().isAfter(end)).toList();
                });
    }

    private void stubCosts(List<StoreCostItem> rows) {
        when(costRepository.findAllByStoreIdAndCostMonthBetweenOrderByCostMonthAsc(eq(20L), any(), any()))
                .thenAnswer(call -> {
                    LocalDate start = call.getArgument(1);
                    LocalDate end = call.getArgument(2);
                    return rows.stream().filter(row -> !row.getCostMonth().isBefore(start)
                            && !row.getCostMonth().isAfter(end)).toList();
                });
    }

    private SalesDailySummaryEntity sales(LocalDate date, long amount) {
        return SalesDailySummaryEntity.create(20L, date, amount, amount, 1, 1);
    }

    private StoreCostItem cost(LocalDate month, long rent, String rate) {
        return StoreCostItem.create(20L, month, rent, 0, new BigDecimal(rate),
                LocalDateTime.of(2026, 10, 7, 9, 0));
    }
}
