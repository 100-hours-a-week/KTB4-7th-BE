package com.memme.service.sales.analysis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesAnalysisServiceTest {

    private SalesDailySummaryRepository dailySummaryRepository;
    private SalesOrderRepository orderRepository;
    private SalesOrderItemRepository itemRepository;
    private SalesAnalysisService service;

    @BeforeEach
    void setUp() {
        dailySummaryRepository = mock(SalesDailySummaryRepository.class);
        orderRepository = mock(SalesOrderRepository.class);
        itemRepository = mock(SalesOrderItemRepository.class);
        service = new SalesAnalysisService(
                dailySummaryRepository,
                orderRepository,
                itemRepository
        );
    }

    @Test
    void calculatesKpisTrendsRankingsCategoriesHoursWeekdaysAndComparison() {
        long storeId = 301L;
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 2);
        LocalDate comparisonStart = LocalDate.of(2026, 8, 1);
        LocalDate comparisonEnd = LocalDate.of(2026, 8, 2);

        SalesDailySummaryEntity firstDay = summary(start, 800, 1);
        SalesDailySummaryEntity secondDay = summary(end, 500, 1);
        SalesDailySummaryEntity comparisonDay = summary(comparisonStart, 1_000, 2);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, start, end
        )).thenReturn(List.of(firstDay, secondDay));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, comparisonStart, comparisonEnd
        )).thenReturn(List.of(comparisonDay));

        SalesOrderEntity morning = order(11L, LocalDateTime.of(2026, 9, 1, 9, 0));
        SalesOrderEntity afternoon = order(12L, LocalDateTime.of(2026, 9, 1, 13, 0));
        SalesOrderEntity nextDay = order(13L, LocalDateTime.of(2026, 9, 2, 13, 0));
        when(orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay()
                )).thenReturn(List.of(morning, afternoon, nextDay));
        SalesOrderItemEntity americano = item(
                101L, 11L, "americano", "아메리카노", 2, 1_000,
                SalesStandardMenuCategory.COFFEE
        );
        SalesOrderItemEntity canceledAmericano = item(
                102L, 12L, "americano", "아메리카노", -1, -200,
                SalesStandardMenuCategory.COFFEE
        );
        SalesOrderItemEntity earlGrey = item(
                103L, 13L, "earl-grey", "얼그레이", 1, 500,
                SalesStandardMenuCategory.TEA
        );
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(
                List.of(11L, 12L, 13L)
        )).thenReturn(List.of(americano, canceledAmericano, earlGrey));

        SalesAnalysisResult result = service.analyze(storeId, "THIS_MONTH", start, end);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            var analysis = completed.analysis();
            assertThat(analysis.comparisonPeriod().startDate()).isEqualTo(comparisonStart);
            assertThat(analysis.comparisonPeriod().endDate()).isEqualTo(comparisonEnd);
            assertThat(analysis.kpis().totalSales()).isEqualByComparingTo("1300");
            assertThat(analysis.kpis().orderCount()).isEqualTo(2);
            assertThat(analysis.kpis().averageOrderValue()).isEqualByComparingTo("650");
            assertThat(analysis.kpis().changes().totalSalesRate()).isEqualByComparingTo("30.0");
            assertThat(analysis.kpis().changes().orderCountRate()).isEqualByComparingTo("0.0");
            assertThat(analysis.kpis().changes().averageOrderValueRate())
                    .isEqualByComparingTo("30.0");
            assertThat(analysis.dailySales())
                    .extracting(daily -> daily.salesAmount().longValueExact())
                    .containsExactly(800L, 500L);
            assertThat(analysis.menuRankings())
                    .extracting(ranking -> ranking.menuName() + ":" + ranking.salesAmount()
                            + ":" + ranking.quantity())
                    .containsExactly("아메리카노:800:1", "얼그레이:500:1");
            assertThat(analysis.hourlySales())
                    .extracting(hourly -> hourly.hour() + ":" + hourly.salesAmount())
                    .containsExactly("9:1000", "13:300");
            assertThat(analysis.weekdaySales()).hasSize(7);
            assertThat(analysis.aiInsight()).isNull();

            assertThat(completed.categories().categories())
                    .extracting(category -> category.categoryName() + ":" + category.netSales()
                            + ":" + category.ratio())
                    .containsExactly("커피:800:61.5", "티:500:38.5");
        });
    }

    @Test
    void returnsEmptyResultWithoutLoadingOrdersWhenPeriodHasNoSalesData() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 3);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of());

        SalesAnalysisResult result = service.analyze(storeId, "TODAY", date, date);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Empty.class, empty -> {
            assertThat(empty.analysis().period().type()).isEqualTo("TODAY");
            assertThat(empty.analysis().kpis()).isNull();
            assertThat(empty.analysis().dailySales()).isEmpty();
        });
        verifyNoInteractions(orderRepository, itemRepository);
    }

    @Test
    void usesStoreIdInEveryRepositoryQuery() {
        long storeId = 901L;
        LocalDate date = LocalDate.of(2026, 9, 8);
        SalesDailySummaryEntity today = summary(date, 100, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of(today));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        )).thenReturn(List.of());
        when(orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()
                )).thenReturn(List.of());

        service.analyze(storeId, "TODAY", date, date);

        verify(dailySummaryRepository).findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        );
        verify(dailySummaryRepository).findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        );
        verify(orderRepository)
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()
                );
    }

    private SalesDailySummaryEntity summary(LocalDate date, long sales, int orderCount) {
        SalesDailySummaryEntity summary = mock(SalesDailySummaryEntity.class);
        when(summary.getSalesDate()).thenReturn(date);
        when(summary.getTotalNetAmount()).thenReturn(sales);
        when(summary.getOrderCount()).thenReturn(orderCount);
        return summary;
    }

    private SalesOrderEntity order(long id, LocalDateTime orderedAt) {
        SalesOrderEntity order = mock(SalesOrderEntity.class);
        when(order.getId()).thenReturn(id);
        when(order.getOrderedAt()).thenReturn(orderedAt);
        return order;
    }

    private SalesOrderItemEntity item(
            long id,
            long orderId,
            String menuKey,
            String menuName,
            int quantity,
            long netAmount,
            SalesStandardMenuCategory category
    ) {
        SalesOrderItemEntity item = mock(SalesOrderItemEntity.class);
        when(item.getId()).thenReturn(id);
        when(item.getSalesOrderId()).thenReturn(orderId);
        when(item.getItemType()).thenReturn(SalesOrderItemType.MENU);
        when(item.getMenuKey()).thenReturn(menuKey);
        when(item.getMenuName()).thenReturn(menuName);
        when(item.getQuantity()).thenReturn(quantity);
        when(item.getNetAmount()).thenReturn(netAmount);
        when(item.getStandardCategory()).thenReturn(category);
        return item;
    }
}
