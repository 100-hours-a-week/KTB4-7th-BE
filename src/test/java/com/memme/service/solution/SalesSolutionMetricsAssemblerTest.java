package com.memme.service.solution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.memme.dto.sales.MenuRankingMetric;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesSolutionMetricsAssemblerTest {

    @Test
    void assemblesTopFiveMenuRankingsAndWeekdayWeekendBusinessHours() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 3));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 4));

        SalesOrderEntity currentOne = order(1L, LocalDateTime.of(2026, 10, 1, 10, 0));
        SalesOrderEntity currentTwo = order(2L, LocalDateTime.of(2026, 10, 2, 11, 0));
        SalesOrderEntity previous = order(3L, LocalDateTime.of(2026, 9, 30, 12, 0));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentOne, currentTwo), List.of(previous));

        List<SalesOrderItemEntity> currentItems = List.of(
                item(1L, "americano", "아메리카노", 600, 6),
                item(2L, "latte", "카페라떼", 500, 5),
                item(1L, "tea", "홍차", 400, 4),
                item(2L, "ade", "레몬에이드", 300, 3),
                item(1L, "cake", "치즈케이크", 200, 2),
                item(2L, "cookie", "쿠키", 100, 1));
        List<SalesOrderItemEntity> previousItems = List.of(
                item(3L, "americano", "아메리카노", 300, 3),
                item(3L, "latte", "카페라떼", 1_000, 10),
                item(3L, "tea", "홍차", 400, 4),
                item(3L, "ade", "레몬에이드", 300, 3),
                item(3L, "cake", "치즈케이크", 100, 1),
                item(3L, "cookie", "쿠키", 200, 2));
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(currentItems)
                .thenReturn(previousItems);
        List<StoreBusinessHours> businessHours = List.of(
                hours(1, true, null, null),
                hours(2, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(3, false, LocalTime.of(10, 0), LocalTime.of(21, 0)),
                hours(4, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(5, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(6, false, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                hours(7, false, LocalTime.of(11, 0), LocalTime.of(20, 0)));
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(businessHours);

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.salesSummary().netSales()).isEqualTo(2_100);
        assertThat(metrics.menuRankings()).containsExactly(
                new MenuRankingMetric(1, "아메리카노", BigDecimal.valueOf(600), 6L,
                        new BigDecimal("0.2857"), new BigDecimal("1.0000")),
                new MenuRankingMetric(2, "카페라떼", BigDecimal.valueOf(500), 5L,
                        new BigDecimal("0.2381"), new BigDecimal("-0.5000")),
                new MenuRankingMetric(3, "홍차", BigDecimal.valueOf(400), 4L,
                        new BigDecimal("0.1905"), new BigDecimal("0.0000")),
                new MenuRankingMetric(4, "레몬에이드", BigDecimal.valueOf(300), 3L,
                        new BigDecimal("0.1429"), new BigDecimal("0.0000")),
                new MenuRankingMetric(5, "치즈케이크", BigDecimal.valueOf(200), 2L,
                        new BigDecimal("0.0952"), new BigDecimal("1.0000")));
        assertThat(metrics.businessHours()).isEqualTo(new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("08:00", "22:00"),
                new SalesSolutionMetrics.BusinessHourBand("09:00", "21:00"),
                List.of("MON")));
    }

    @Test
    void omitsMenuRankingsWhenNegativeLowerRankedMenuMakesRatioExceedOne() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        SalesOrderEntity currentOrder = order(1L, LocalDateTime.of(2026, 10, 1, 10, 0));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentOrder))
                .thenReturn(List.of());
        List<SalesOrderItemEntity> currentItems = List.of(
                item(1L, "top", "인기 메뉴", 120, 12),
                item(1L, "second", "메뉴 2", 10, 1),
                item(1L, "third", "메뉴 3", 10, 1),
                item(1L, "fourth", "메뉴 4", 10, 1),
                item(1L, "fifth", "메뉴 5", 10, 1),
                item(1L, "refund", "환불 메뉴", -60, -1));
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(currentItems)
                .thenReturn(List.of());
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.salesSummary().netSales()).isEqualTo(100);
        assertThat(metrics.menuRankings()).isNull();
    }

    @Test
    void representsEqualOpenAndCloseTimesAsTwentyFourHourBusiness() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        List<StoreBusinessHours> schedules = List.of(
                hours(1, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(2, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(3, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(4, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(5, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(6, false, LocalTime.of(9, 0), LocalTime.MIDNIGHT),
                hours(7, false, LocalTime.of(9, 0), LocalTime.MIDNIGHT));
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(schedules);

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.menuRankings()).isNull();
        assertThat(metrics.businessHours()).isEqualTo(new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("00:00", "24:00"),
                new SalesSolutionMetrics.BusinessHourBand("09:00", "24:00"),
                null));
    }

    @Test
    void omitsBusinessHoursWhenNonMidnightOpenAndCloseTimesAreEqual() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        List<StoreBusinessHours> schedules = List.of(
                hours(1, false, LocalTime.of(8, 0), LocalTime.of(8, 0)),
                hours(2, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(3, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(4, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(5, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(6, false, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                hours(7, false, LocalTime.of(9, 0), LocalTime.of(21, 0)));
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(schedules);

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.businessHours()).isNull();
    }

    @Test
    void excludesForecastTargetDateSalesFromSolutionMetrics() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 7, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 2));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        ArgumentCaptor<LocalDateTime> endExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, org.mockito.Mockito.times(2))
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        eq(10L), any(LocalDateTime.class), endExclusive.capture());
        assertThat(endExclusive.getAllValues().getFirst())
                .isEqualTo(LocalDate.of(2026, 10, 2).atStartOfDay());
        assertThat(metrics.menuRankings()).isNull();
        assertThat(metrics.businessHours()).isNull();
    }

    private SalesOrderEntity order(Long id, LocalDateTime orderedAt) {
        SalesOrderEntity order = mock(SalesOrderEntity.class);
        when(order.getId()).thenReturn(id);
        when(order.isValid()).thenReturn(true);
        when(order.getOrderedAt()).thenReturn(orderedAt);
        return order;
    }

    private SalesOrderItemEntity item(Long orderId, String key, String name, long netAmount, int quantity) {
        SalesOrderItemEntity item = mock(SalesOrderItemEntity.class);
        when(item.getSalesOrderId()).thenReturn(orderId);
        when(item.getItemType()).thenReturn(SalesOrderItemType.MENU);
        when(item.getMenuKey()).thenReturn(key);
        when(item.getMenuName()).thenReturn(name);
        when(item.getNetAmount()).thenReturn(netAmount);
        when(item.getQuantity()).thenReturn(quantity);
        when(item.getStandardCategory()).thenReturn(SalesStandardMenuCategory.COFFEE);
        return item;
    }

    private StoreBusinessHours hours(int day, boolean closed, LocalTime opensAt, LocalTime closesAt) {
        StoreBusinessHours hours = mock(StoreBusinessHours.class);
        when(hours.getDayOfWeek()).thenReturn(day);
        when(hours.isClosed()).thenReturn(closed);
        when(hours.getOpensAt()).thenReturn(opensAt);
        when(hours.getClosesAt()).thenReturn(closesAt);
        return hours;
    }
}
