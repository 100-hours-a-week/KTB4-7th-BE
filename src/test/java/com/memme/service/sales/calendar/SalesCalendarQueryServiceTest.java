package com.memme.service.sales.calendar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.solution.WeatherCalendarCondition;
import com.memme.entity.solution.WeatherCalendarConditionEntity;
import com.memme.entity.store.Store;
import com.memme.exception.sales.SalesCalendarInputException;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.solution.WeatherCalendarConditionRepository;
import com.memme.repository.store.StoreRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class SalesCalendarQueryServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final SalesDailySummaryRepository summaries = mock(SalesDailySummaryRepository.class);
    private final WeatherCalendarConditionRepository weather = mock(WeatherCalendarConditionRepository.class);
    private final Store store = mock(Store.class);
    private SalesCalendarQueryService service;

    @BeforeEach
    void setUp() {
        when(store.getId()).thenReturn(301L);
        when(store.getWeatherStationId()).thenReturn(null);
        when(store.getCreatedAt()).thenReturn(LocalDateTime.of(2024, 2, 15, 0, 0));
        when(stores.findByOwnerId(7L)).thenReturn(Optional.of(store));
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        service = new SalesCalendarQueryService(stores, summaries, weather,
                Clock.fixed(Instant.parse("2026-10-27T15:01:00Z"), ZoneOffset.UTC));
    }

    @Test
    void usesSeoulTodayAndResolvesOwnedStoreFromUserId() {
        var response = service.query(7L, null, null);
        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(response.days()).hasSize(31);
        assertThat(response.days()).filteredOn(day -> day.isSelected()).hasSize(1);
        assertThat(response.days().get(28).dataStatus()).isEqualTo("FUTURE");
        assertThat(response.monthSummary().averageDailySalesAmount()).isNull();
        verify(summaries).findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                301L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        verifyNoInteractions(weather);
    }

    @Test
    void aggregatesOnlyCompleteActiveDaysIncludingRefundOnlyAndZeroNetDays() {
        when(store.getWeatherStationId()).thenReturn(108L);
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of(day(1, 120, 1, 1, SalesDailyStatus.COMPLETE),
                        day(2, 80, 1, 1, SalesDailyStatus.COMPLETE),
                        day(3, 300, 3, 3, SalesDailyStatus.COMPLETE),
                        day(4, -100, 0, 1, SalesDailyStatus.COMPLETE),
                        day(5, 0, 1, 2, SalesDailyStatus.COMPLETE),
                        day(6, 999, 1, 0, SalesDailyStatus.COMPLETE),
                        day(7, 999, 1, 1, SalesDailyStatus.MISSING),
                        day(8, 999, 1, 1, SalesDailyStatus.CLOSED),
                        day(9, 999, 1, 1, SalesDailyStatus.UNKNOWN),
                        day(30, 999, 1, 1, SalesDailyStatus.COMPLETE)));
        when(weather.findAllByStationIdAndObservationDateBetweenOrderByObservationDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of(WeatherCalendarConditionEntity.create(108L, LocalDate.of(2026, 10, 1),
                        WeatherCalendarCondition.RAIN, LocalDateTime.of(2026, 10, 1, 12, 0), "KMA_ASOS")));
        var response = service.query(7L, "2026-10", "2026-10-04");
        assertThat(response.monthSummary().totalSalesAmount()).isEqualTo(400);
        assertThat(response.monthSummary().totalOrderCount()).isEqualTo(6);
        assertThat(response.monthSummary().dataDayCount()).isEqualTo(5);
        assertThat(response.monthSummary().averageDailySalesAmount()).isEqualTo(80);
        assertThat(response.days().getFirst().weather().condition()).isEqualTo("RAIN");
        assertThat(response.days().get(1).weather()).isNull();
        assertThat(response.selectedDay().salesAmount()).isEqualTo(-100);
        assertThat(response.selectedDay().orderCount()).isZero();
        assertThat(response.selectedDay().averageOrderAmount()).isNull();
        assertThat(response.days().get(5).salesAmount()).isNull();
        assertThat(response.days().get(29).salesAmount()).isNull();
    }

    @Test
    void comparesThresholdsBeforeRoundingTheMonthlyAverage() {
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of(day(1, 1, 1, 1, SalesDailyStatus.COMPLETE),
                        day(2, 2, 1, 1, SalesDailyStatus.COMPLETE)));
        var response = service.query(7L, "2026-10", null);
        assertThat(response.monthSummary().averageDailySalesAmount()).isEqualTo(2);
        assertThat(response.days().getFirst().salesLevel()).isEqualTo("LOW");
        assertThat(response.days().get(1).salesLevel()).isEqualTo("HIGH");
    }

    @Test
    void keepsNonPositiveMonthlyAverageWithoutHighLowHighlight() {
        when(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(anyLong(), any(), any()))
                .thenReturn(List.of(day(1, -100, 0, 1, SalesDailyStatus.COMPLETE)));
        var response = service.query(7L, "2026-10", "2026-10-01");
        assertThat(response.monthSummary().averageDailySalesAmount()).isEqualTo(-100);
        assertThat(response.days().getFirst().salesLevel()).isEqualTo("UNAVAILABLE");
        assertThat(response.selectedDay().insight().deviationRate()).isNull();
    }

    @Test
    void leapMonthDefaultsToFirstDay() {
        var response = service.query(7L, "2024-02", null);
        assertThat(response.days()).hasSize(29);
        assertThat(response.selectedDate()).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01", "2026-11"})
    void rejectsMonthsOutsideServiceRange(String month) {
        assertThatThrownBy(() -> service.query(7L, month, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(416));
        verifyNoInteractions(weather);
    }

    @Test
    void rejectsBothMalformedFieldsTogether() {
        assertThatThrownBy(() -> service.query(7L, "2026-13", "2026-02-30"))
                .isInstanceOfSatisfying(SalesCalendarInputException.class,
                        exception -> assertThat(exception.getFieldErrors()).extracting(error -> error.field())
                                .containsExactly("month", "selectedDate"));
    }

    @Test
    void rejectsSelectedDateInAnotherMonth() {
        assertThatThrownBy(() -> service.query(7L, "2026-10", "2026-09-30"))
                .isInstanceOf(SalesCalendarInputException.class);
    }

    @Test
    void cannotUseAnotherUsersStore() {
        when(stores.findByOwnerId(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.query(8L, null, null)).isInstanceOf(StoreProfileNotFoundException.class);
        verifyNoInteractions(summaries, weather);
    }

    private SalesDailySummaryEntity day(int day, long amount, int orders, int activity, SalesDailyStatus status) {
        return SalesDailySummaryEntity.create(301L, LocalDate.of(2026, 10, day), amount, amount,
                orders, 0, activity, status);
    }
}
