package com.memme.service.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.solution.WeatherObservationEntity;
import com.memme.entity.store.Store;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.solution.WeatherObservationRepository;
import com.memme.repository.store.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionPatternsServiceTest {

    private static final Long STORE_ID = 10L;
    private static final Long STATION_ID = 42L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 19);
    private static final LocalDate WINDOW_START = LocalDate.of(2026, 7, 27);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 10, 18);

    private StoreRepository storeRepository;
    private WeatherObservationRepository observationRepository;
    private SalesDailySummaryRepository dailySummaryRepository;
    private SalesOrderRepository orderRepository;
    private SalesOrderItemRepository itemRepository;
    private ConditionPatternsService service;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        observationRepository = mock(WeatherObservationRepository.class);
        dailySummaryRepository = mock(SalesDailySummaryRepository.class);
        orderRepository = mock(SalesOrderRepository.class);
        itemRepository = mock(SalesOrderItemRepository.class);
        service = new ConditionPatternsService(
                storeRepository,
                observationRepository,
                dailySummaryRepository,
                orderRepository,
                itemRepository
        );

        Store store = mock(Store.class);
        when(store.getWeatherStationId()).thenReturn(STATION_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }

    @Test
    void calculatesRainAndDryMenuAveragesAcrossTheLatestTwelveCompleteWeeks() {
        List<LocalDate> rainDates = List.of(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2)
        );
        List<LocalDate> dryDates = List.of(
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 4),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 8, 8)
        );
        List<WeatherObservationEntity> observations = new ArrayList<>();
        rainDates.forEach(date -> observations.add(observation(date, "1.0")));
        dryDates.forEach(date -> observations.add(observation(date, "0.0")));
        when(observationRepository.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                STATION_ID, WINDOW_START, WINDOW_END)).thenReturn(observations);

        List<LocalDate> observedSalesDates = new ArrayList<>();
        observedSalesDates.addAll(rainDates);
        observedSalesDates.addAll(dryDates);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                STORE_ID, WINDOW_START, WINDOW_END)).thenReturn(observedSalesDates.stream()
                .map(date -> SalesDailySummaryEntity.create(STORE_ID, date, 0,
                        rainDates.contains(date) ? 3_000 : 5_000, 0, 0, SalesDailyStatus.COMPLETE))
                .toList());

        List<SalesOrderEntity> orders = new ArrayList<>();
        List<SalesOrderItemEntity> items = new ArrayList<>();
        long id = 1;
        for (LocalDate date : rainDates) {
            orders.add(order(id, date));
            items.add(item(id++, "ice-americano", "아이스 아메리카노", 4, 1_000));
        }
        for (LocalDate date : dryDates) {
            orders.add(order(id, date));
            items.add(item(id++, "ice-americano", "아이스 아메리카노", 8, 2_000));
        }
        when(orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        eq(STORE_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(orders);
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(items);

        List<SalesSolutionMetrics.ConditionPattern> patterns = service.resolve(STORE_ID, TARGET_DATE);

        assertThat(patterns).containsExactly(new SalesSolutionMetrics.ConditionPattern(
                "RAIN",
                12,
                1.0,
                5,
                5,
                0.6,
                0.8,
                List.of(new SalesSolutionMetrics.ConditionMenu(
                        "아이스 아메리카노", 4, 8, -0.5, 1_000, 2_000)),
                3_000,
                5_000
        ));
    }

    @Test
    void excludesLightRainMissingObservationsAndNonCompleteSalesDays() {
        List<ConditionDay> days = comparisonDays();
        days.add(conditionDay(LocalDate.of(2026, 8, 9), "0.099", SalesDailyStatus.COMPLETE, 8, 2_000));
        days.add(conditionDay(LocalDate.of(2026, 8, 10), "0.1", SalesDailyStatus.COMPLETE, 100, 100_000));
        days.add(conditionDay(LocalDate.of(2026, 8, 11), "0.999", SalesDailyStatus.COMPLETE, 100, 100_000));
        days.add(conditionDay(LocalDate.of(2026, 8, 12), "2.0", SalesDailyStatus.CLOSED, 100, 100_000));
        days.add(conditionDay(LocalDate.of(2026, 8, 13), "0.0", SalesDailyStatus.MISSING, 100, 100_000));
        days.add(conditionDay(LocalDate.of(2026, 8, 14), null, SalesDailyStatus.COMPLETE, 100, 100_000));
        days.add(new ConditionDay(LocalDate.of(2026, 8, 15), false, null, SalesDailyStatus.COMPLETE, 100, 100_000));
        givenDays(days);

        List<SalesSolutionMetrics.ConditionPattern> patterns = service.resolve(STORE_ID, TARGET_DATE);

        assertThat(patterns).containsExactly(new SalesSolutionMetrics.ConditionPattern(
                "RAIN", 12, 1.0, 5, 6, 0.6, 0.6667,
                List.of(new SalesSolutionMetrics.ConditionMenu(
                        "아이스 아메리카노", 4, 8, -0.5, 1_000, 2_000))));
    }

    @Test
    void omitsPatternsWhenStoreHasNoWeatherStationMapping() {
        Store unmappedStore = mock(Store.class);
        when(unmappedStore.getWeatherStationId()).thenReturn(null);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(unmappedStore));

        assertThat(service.resolve(STORE_ID, TARGET_DATE)).isNull();

        verify(observationRepository, never())
                .findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                        any(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void omitsPatternsWhenEitherConditionHasFewerThanFiveUsableDays() {
        List<ConditionDay> days = comparisonDays();
        days.remove(4);
        givenDays(days);

        assertThat(service.resolve(STORE_ID, TARGET_DATE)).isNull();
    }

    @Test
    void selectsThreeLargestChangesAndExcludesMenusBelowBaselineAverageThreshold() {
        List<ConditionDay> days = new ArrayList<>();
        rainDates().forEach(date -> days.add(new ConditionDay(date, true, "1.0", SalesDailyStatus.COMPLETE,
                0, 0, List.of(
                        new MenuData("b", "메뉴 B", 12, 12_000),
                        new MenuData("c", "메뉴 C", 4, 4_000),
                        new MenuData("d", "메뉴 D", 8, 8_000),
                        new MenuData("low", "저판매 메뉴", 10, 10_000),
                        new MenuData("three", "기준 3개 메뉴", 6, 6_000)))));
        for (int index = 0; index < dryDates().size(); index++) {
            int lowQuantity = index == 0 ? 6 : 2;
            LocalDate date = dryDates().get(index);
            days.add(new ConditionDay(date, true, "0.0", SalesDailyStatus.COMPLETE, 0, 0, List.of(
                    new MenuData("a", "메뉴 A", 8, 8_000),
                    new MenuData("b", "메뉴 B", 8, 8_000),
                    new MenuData("c", "메뉴 C", 8, 8_000),
                    new MenuData("d", "메뉴 D", 8, 8_000),
                    new MenuData("low", "저판매 메뉴", lowQuantity, lowQuantity * 1_000L),
                    new MenuData("three", "기준 3개 메뉴", 3, 3_000))));
        }
        givenDays(days);

        List<SalesSolutionMetrics.ConditionPattern> patterns = service.resolve(STORE_ID, TARGET_DATE);

        assertThat(patterns).hasSize(1);
        assertThat(patterns.getFirst().menus()).containsExactly(
                new SalesSolutionMetrics.ConditionMenu("기준 3개 메뉴", 6, 3, 1.0, 6_000, 3_000),
                new SalesSolutionMetrics.ConditionMenu("메뉴 A", 0, 8, -1.0, 0, 8_000),
                new SalesSolutionMetrics.ConditionMenu("메뉴 B", 12, 8, 0.5, 12_000, 8_000)
        );
    }

    private List<ConditionDay> comparisonDays() {
        List<ConditionDay> days = new ArrayList<>();
        rainDates().forEach(date -> days.add(conditionDay(date, "1.0", SalesDailyStatus.COMPLETE, 4, 1_000)));
        dryDates().forEach(date -> days.add(conditionDay(date, "0.0", SalesDailyStatus.COMPLETE, 8, 2_000)));
        return days;
    }

    private List<LocalDate> rainDates() {
        return List.of(
                LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));
    }

    private List<LocalDate> dryDates() {
        return List.of(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 8));
    }

    private ConditionDay conditionDay(
            LocalDate date,
            String precipitation,
            SalesDailyStatus status,
            int quantity,
            long amount
    ) {
        return new ConditionDay(date, true, precipitation, status, quantity, amount);
    }

    private void givenDays(List<ConditionDay> days) {
        when(observationRepository.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                STATION_ID, WINDOW_START, WINDOW_END)).thenReturn(days.stream()
                .filter(ConditionDay::hasObservation)
                .map(data -> observation(data.date(), data.precipitation()))
                .toList());
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                STORE_ID, WINDOW_START, WINDOW_END)).thenReturn(days.stream()
                .map(data -> SalesDailySummaryEntity.create(
                        STORE_ID, data.date(), 0, 0, 0, 0, data.salesStatus()))
                .toList());

        List<SalesOrderEntity> orders = new ArrayList<>();
        List<SalesOrderItemEntity> items = new ArrayList<>();
        long orderId = 1;
        for (ConditionDay data : days) {
            if (data.quantity() != 0 || data.amount() != 0) {
                orders.add(order(orderId, data.date()));
                items.add(item(orderId++, "ice", "아이스 아메리카노", data.quantity(), data.amount()));
            }
            for (MenuData menu : data.menus()) {
                if (orders.isEmpty() || !orders.getLast().getOrderedAt().toLocalDate().equals(data.date())) {
                    orders.add(order(orderId++, data.date()));
                }
                items.add(item(orders.getLast().getId(), menu.key(), menu.name(), menu.quantity(), menu.amount()));
            }
        }
        when(orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        eq(STORE_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(orders);
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(items);
    }

    private WeatherObservationEntity observation(LocalDate date, String precipitation) {
        return WeatherObservationEntity.create(
                STATION_ID,
                date,
                new BigDecimal("37.5000000"),
                new BigDecimal("127.0000000"),
                "RAIN",
                null,
                precipitation == null ? null : new BigDecimal(precipitation),
                "KMA_ASOS"
        );
    }

    private SalesDailySummaryEntity completeDay(LocalDate date) {
        return SalesDailySummaryEntity.create(STORE_ID, date, 0, 0, 0, 0, SalesDailyStatus.COMPLETE);
    }

    private SalesOrderEntity order(long id, LocalDate date) {
        SalesOrderEntity order = mock(SalesOrderEntity.class);
        when(order.getId()).thenReturn(id);
        when(order.isValid()).thenReturn(true);
        when(order.getOrderedAt()).thenReturn(date.atTime(12, 0));
        return order;
    }

    private SalesOrderItemEntity item(long orderId, String key, String name, int quantity, long amount) {
        SalesOrderItemEntity item = mock(SalesOrderItemEntity.class);
        when(item.getSalesOrderId()).thenReturn(orderId);
        when(item.getItemType()).thenReturn(SalesOrderItemType.MENU);
        when(item.getMenuKey()).thenReturn(key);
        when(item.getMenuName()).thenReturn(name);
        when(item.getQuantity()).thenReturn(quantity);
        when(item.getNetAmount()).thenReturn(amount);
        return item;
    }

    private record ConditionDay(
            LocalDate date,
            boolean hasObservation,
            String precipitation,
            SalesDailyStatus salesStatus,
            int quantity,
            long amount,
            List<MenuData> menus
    ) {
        private ConditionDay(
                LocalDate date,
                boolean hasObservation,
                String precipitation,
                SalesDailyStatus salesStatus,
                int quantity,
                long amount
        ) {
            this(date, hasObservation, precipitation, salesStatus, quantity, amount, List.of());
        }
    }

    private record MenuData(String key, String name, int quantity, long amount) {}
}
