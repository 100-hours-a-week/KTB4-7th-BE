package com.memme.service.sales.analysis;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Field;

import com.memme.dto.sales.SalesAnalysisResponse;
import com.memme.dto.sales.SalesCategoriesResponse;
import com.memme.dto.sales.SalesPeriod;
import com.memme.dto.sales.InternalSalesSummaryResponse;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemStatus;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.dto.sales.InternalSalesHourlyProfilesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalSalesQueryServiceTest {

    @Mock
    private SalesAnalysisQueryService periodResolver;
    @Mock
    private SalesAnalysisService analysisService;
    @Mock
    private SalesOrderRepository orderRepository;
    @Mock
    private SalesOrderItemRepository itemRepository;
    @Mock
    private SalesForecastRepository forecastRepository;

    private InternalSalesQueryService service;

    @BeforeEach
    void setUp() {
        service = new InternalSalesQueryService(
                periodResolver,
                analysisService,
                orderRepository,
                itemRepository,
                forecastRepository,
                Clock.fixed(
                        Instant.parse("2026-09-26T01:00:00Z"),
                        ZoneId.of("Asia/Seoul")
                )
        );
    }

    @Test
    void 매출_요약을_AI_툴_계약으로_반환한다() {
        SalesPeriod period = new SalesPeriod(
                "THIS_MONTH",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 26)
        );
        when(periodResolver.resolvePeriod("THIS_MONTH", null, null)).thenReturn(period);
        SalesAnalysisResponse analysis = new SalesAnalysisResponse(
                period,
                new SalesAnalysisResponse.DateRange(
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 26)
                ),
                new SalesAnalysisResponse.Kpis(
                        new BigDecimal("3200000"),
                        420,
                        new BigDecimal("7619"),
                        new SalesAnalysisResponse.Changes(
                                new BigDecimal("0.125"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
        when(analysisService.analyze(
                2L,
                "THIS_MONTH",
                period.startDate(),
                period.endDate()
        )).thenReturn(new SalesAnalysisResult.Completed(
                analysis,
                new SalesCategoriesResponse(List.of(), List.of())
        ));

        InternalSalesSummaryResponse response = service.summary(2L, "THIS_MONTH", null, null);

        assertThat(response.totalSales()).isEqualByComparingTo("3200000");
        assertThat(response.orderCount()).isEqualTo(420);
        assertThat(response.changeRate()).isEqualByComparingTo("0.125");
    }

    @Test
    void 시간대_매출은_요청한_요일의_MENU_항목만_집계한다() throws Exception {
        SalesPeriod period = new SalesPeriod(
                "THIS_WEEK",
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 26)
        );
        when(periodResolver.resolvePeriod("THIS_WEEK", null, null)).thenReturn(period);
        when(analysisService.analyze(2L, "THIS_WEEK", period.startDate(), period.endDate()))
                .thenReturn(completed(period));
        SalesOrderEntity fridayOrder = order(11L, LocalDate.of(2026, 9, 25).atTime(12, 0));
        SalesOrderEntity saturdayOrder = order(12L, LocalDate.of(2026, 9, 26).atTime(12, 0));
        when(orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        2L,
                        period.startDate().atStartOfDay(),
                        period.endDate().plusDays(1).atStartOfDay()
                )).thenReturn(List.of(fridayOrder, saturdayOrder));
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(Set.of(12L)))
                .thenReturn(List.of(
                        item(12L, 420_000L, SalesOrderItemType.MENU),
                        item(12L, 10_000L, SalesOrderItemType.DELIVERY_FEE)
                ));

        InternalSalesHourlyProfilesResponse response = service.hourlyProfiles(
                2L,
                "THIS_WEEK",
                "SATURDAY",
                null,
                null
        );

        assertThat(response.hourlyProfiles()).containsExactly(
                new InternalSalesHourlyProfilesResponse.HourlyProfile(
                        12,
                        new BigDecimal("420000"),
                        1
                )
        );
    }

    private SalesAnalysisResult.Completed completed(SalesPeriod period) {
        SalesAnalysisResponse analysis = new SalesAnalysisResponse(
                period,
                new SalesAnalysisResponse.DateRange(period.startDate(), period.endDate()),
                new SalesAnalysisResponse.Kpis(
                        BigDecimal.ONE,
                        1,
                        BigDecimal.ONE,
                        new SalesAnalysisResponse.Changes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
        return new SalesAnalysisResult.Completed(
                analysis,
                new SalesCategoriesResponse(List.of(), List.of())
        );
    }

    private SalesOrderEntity order(Long id, java.time.LocalDateTime orderedAt) throws Exception {
        SalesOrderEntity order = SalesOrderEntity.create(
                1L,
                2L,
                SalesOrderChannel.POS,
                "order-" + id,
                orderedAt,
                true
        );
        setId(order, id);
        return order;
    }

    private SalesOrderItemEntity item(
            Long orderId,
            long netAmount,
            SalesOrderItemType itemType
    ) {
        return SalesOrderItemEntity.create(new SalesOrderItemEntity.Values(
                orderId,
                null,
                SalesOrderItemStatus.COMPLETED,
                "아메리카노",
                "아메리카노",
                "americano",
                "커피",
                SalesStandardMenuCategory.COFFEE,
                null,
                1,
                netAmount,
                netAmount,
                0,
                null,
                0,
                null,
                0,
                netAmount,
                true,
                0,
                itemType
        ));
    }

    private void setId(SalesOrderEntity order, Long id) throws Exception {
        Field idField = SalesOrderEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(order, id);
    }
}
