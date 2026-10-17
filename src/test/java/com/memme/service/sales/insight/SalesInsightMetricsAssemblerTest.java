package com.memme.service.sales.insight;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesDailyStatus;
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
        when(summaries.findAllByStoreIdOrderBySalesDateAsc(1L)).thenReturn(current);
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

    @Test
    void treatsFourteenContinuousDaysAcrossMonthBoundaryAsSufficient() {
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
        SalesDailySummaryEntity augustLastDay = SalesDailySummaryEntity.create(
                1L,
                LocalDate.of(2026, 8, 31),
                100_000,
                80_000,
                10,
                12
        );
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, september.atDay(1), september.atEndOfMonth()
        )).thenReturn(current);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, september.minusMonths(1).atDay(1), september.minusMonths(1).atEndOfMonth()
        )).thenReturn(List.of(augustLastDay));
        when(summaries.findAllByStoreIdOrderBySalesDateAsc(1L))
                .thenReturn(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(augustLastDay),
                        current.stream()
                ).toList());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, september.atDay(1).atStartOfDay(), september.atEndOfMonth().plusDays(1).atStartOfDay()
        )).thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L,
                september.minusMonths(1).atDay(1).atStartOfDay(),
                september.minusMonths(1).atEndOfMonth().plusDays(1).atStartOfDay()
        )).thenReturn(List.of());

        assertThat(assembler.assemble(1L, september).sufficientData()).isTrue();
    }

    @Test
    void treatsAnOlderFourteenDayRunAsInsufficientWhenTheTargetMonthHasNoSales() {
        SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
        var assembler = new SalesInsightMetricsAssembler(summaries, orders, items);
        YearMonth september = YearMonth.of(2026, 9);
        List<SalesDailySummaryEntity> augustRun = IntStream.rangeClosed(1, 14)
                .mapToObj(day -> SalesDailySummaryEntity.create(
                        1L,
                        LocalDate.of(2026, 8, day),
                        100_000,
                        80_000,
                        10,
                        12
                ))
                .toList();
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, september.atDay(1), september.atEndOfMonth()
        )).thenReturn(List.of());
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, september.minusMonths(1).atDay(1), september.minusMonths(1).atEndOfMonth()
        )).thenReturn(augustRun);
        when(summaries.findAllByStoreIdOrderBySalesDateAsc(1L)).thenReturn(augustRun);

        assertThat(assembler.assemble(1L, september).sufficientData()).isFalse();
    }

    @Test
    void onlyMarksAnEndedMonthWithEveryDayCompleteOrClosedAsDistrictBenchmarkEligible() {
        YearMonth october = YearMonth.of(2026, 10);
        Clock novemberClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
        SalesInsightMetricsAssembler assembler = new SalesInsightMetricsAssembler(
                summaries, orders, items, novemberClock);
        List<SalesDailySummaryEntity> completeMonth = IntStream.rangeClosed(1, october.lengthOfMonth())
                .mapToObj(day -> SalesDailySummaryEntity.create(
                        1L,
                        october.atDay(day),
                        100_000,
                        80_000,
                        10,
                        12,
                        day == 1 ? SalesDailyStatus.CLOSED : SalesDailyStatus.COMPLETE
                ))
                .toList();
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, october.atDay(1), october.atEndOfMonth())).thenReturn(completeMonth);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, october.minusMonths(1).atDay(1), october.minusMonths(1).atEndOfMonth()))
                .thenReturn(List.of());
        when(summaries.findAllByStoreIdOrderBySalesDateAsc(1L)).thenReturn(completeMonth);
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, october.atDay(1).atStartOfDay(), october.atEndOfMonth().plusDays(1).atStartOfDay()))
                .thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, october.minusMonths(1).atDay(1).atStartOfDay(),
                october.minusMonths(1).atEndOfMonth().plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        SalesInsightMetricsAssembler.Result result = assembler.assemble(1L, october);

        assertThat(result.districtBenchmarkEligible()).isTrue();
    }

    @Test
    void omitsDistrictBenchmarkWhenAnyCalendarDayIsUnknownOrMissing() {
        YearMonth october = YearMonth.of(2026, 10);
        Clock novemberClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
        SalesInsightMetricsAssembler assembler = new SalesInsightMetricsAssembler(
                summaries, orders, items, novemberClock);
        List<SalesDailySummaryEntity> incompleteMonth = IntStream.rangeClosed(1, october.lengthOfMonth())
                .mapToObj(day -> SalesDailySummaryEntity.create(
                        1L,
                        october.atDay(day),
                        100_000,
                        80_000,
                        10,
                        12,
                        day == 14 ? SalesDailyStatus.UNKNOWN : SalesDailyStatus.COMPLETE
                ))
                .toList();
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, october.atDay(1), october.atEndOfMonth())).thenReturn(incompleteMonth);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, october.minusMonths(1).atDay(1), october.minusMonths(1).atEndOfMonth()))
                .thenReturn(List.of());
        when(summaries.findAllByStoreIdOrderBySalesDateAsc(1L)).thenReturn(incompleteMonth);
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, october.atDay(1).atStartOfDay(), october.atEndOfMonth().plusDays(1).atStartOfDay()))
                .thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, october.minusMonths(1).atDay(1).atStartOfDay(),
                october.minusMonths(1).atEndOfMonth().plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        SalesInsightMetricsAssembler.Result result = assembler.assemble(1L, october);

        assertThat(result.districtBenchmarkEligible()).isFalse();
    }

    @Test
    void doesNotMarkTheCurrentMonthEligibleEvenWhenEveryDateHasACompletedStatus() {
        YearMonth october = YearMonth.of(2026, 10);
        Clock octoberClock = Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        SalesOrderItemRepository items = mock(SalesOrderItemRepository.class);
        SalesInsightMetricsAssembler assembler = new SalesInsightMetricsAssembler(
                summaries, orders, items, octoberClock);
        List<SalesDailySummaryEntity> currentMonth = IntStream.rangeClosed(1, october.lengthOfMonth())
                .mapToObj(day -> SalesDailySummaryEntity.create(
                        1L,
                        october.atDay(day),
                        100_000,
                        80_000,
                        10,
                        12,
                        SalesDailyStatus.COMPLETE
                ))
                .toList();
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, october.atDay(1), october.atEndOfMonth())).thenReturn(currentMonth);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L, october.minusMonths(1).atDay(1), october.minusMonths(1).atEndOfMonth()))
                .thenReturn(List.of());
        when(summaries.findAllByStoreIdOrderBySalesDateAsc(1L)).thenReturn(currentMonth);
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, october.atDay(1).atStartOfDay(), october.atEndOfMonth().plusDays(1).atStartOfDay()))
                .thenReturn(List.of());
        when(orders.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                1L, october.minusMonths(1).atDay(1).atStartOfDay(),
                october.minusMonths(1).atEndOfMonth().plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        SalesInsightMetricsAssembler.Result result = assembler.assemble(1L, october);

        assertThat(result.districtBenchmarkEligible()).isFalse();
    }
}
