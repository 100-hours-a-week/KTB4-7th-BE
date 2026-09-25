package com.memme.service.sales.insight;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesInsightMetricsAssemblerTest {

    @Test
    void separatesTotalSalesFromMenuSalesAndUsesFourteenDayThreshold() {
        SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
        var assembler = new SalesInsightMetricsAssembler(summaries, orders, items);
        YearMonth september = YearMonth.of(2026, 9);
        List<SalesDailySummaryEntity> current = IntStream.rangeClosed(1, 14)
                .mapToObj(day -> SalesDailySummaryEntity.create(
                        1L,
                        LocalDate.of(2026, 9, day),
                        100_000,
                        80_000,
                        10,
                        12
                ))
                .toList();
        List<SalesDailySummaryEntity> previous = List.of(SalesDailySummaryEntity.create(
                1L,
                LocalDate.of(2026, 8, 1),
                1_000_000,
                700_000,
                100,
                120
        ));
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, september.atDay(1), september.atEndOfMonth()
        )).thenReturn(current);
        YearMonth august = september.minusMonths(1);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, august.atDay(1), august.atEndOfMonth()
        )).thenReturn(previous);
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, september.atDay(1).atStartOfDay(), september.atEndOfMonth().plusDays(1).atStartOfDay()
        )).thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, august.atDay(1).atStartOfDay(), august.atEndOfMonth().plusDays(1).atStartOfDay()
        )).thenReturn(List.of());

        SalesInsightMetricsAssembler.Result result = assembler.assemble(1L, september);

        assertThat(result.sufficientData()).isTrue();
        assertThat(result.metrics().salesSummary().totalSales()).isEqualByComparingTo("1400000");
        assertThat(result.metrics().salesSummary().menuSales()).isEqualByComparingTo("1120000");
        assertThat(result.metrics().salesSummary().orderCount()).isEqualTo(140L);
        assertThat(result.metrics().salesSummary().vsPrevPeriod()).isEqualByComparingTo("0.4000");
        assertThat(result.metrics().salesTrend()).hasSize(14)
                .allSatisfy(metric -> assertThat(metric.menuSales()).isEqualByComparingTo("80000"));
    }

    @Test
    void thirteenSalesDaysAreInsufficient() {
        SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
        var assembler = new SalesInsightMetricsAssembler(summaries, orders, items);
        YearMonth september = YearMonth.of(2026, 9);
        List<SalesDailySummaryEntity> current = IntStream.rangeClosed(1, 13)
                .mapToObj(day -> SalesDailySummaryEntity.create(
                        1L,
                        LocalDate.of(2026, 9, day),
                        100_000,
                        80_000,
                        10,
                        12
                ))
                .toList();
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, september.atDay(1), september.atEndOfMonth()
        )).thenReturn(current);
        YearMonth august = september.minusMonths(1);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, august.atDay(1), august.atEndOfMonth()
        )).thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, september.atDay(1).atStartOfDay(), september.atEndOfMonth().plusDays(1).atStartOfDay()
        )).thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, august.atDay(1).atStartOfDay(), august.atEndOfMonth().plusDays(1).atStartOfDay()
        )).thenReturn(List.of());

        assertThat(assembler.assemble(1L, september).sufficientData()).isFalse();
    }
}
