package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionMetrics.WeatherContext;
import com.memme.dto.solution.external.KmaVillageForecastResponse;
import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.entity.store.Store;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.solution.external.KmaVillageForecastService;
import com.memme.service.solution.external.WeatherContextSummaryService;
import com.memme.service.store.StoreCoordinates;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolutionWeatherContextServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private StoreRepository storeRepository;
    private StoreBusinessHoursRepository hoursRepository;
    private KmaVillageForecastService forecastService;
    private WeatherContextSummaryService summaryService;
    private WeatherForecastSummaryPersistenceService persistenceService;
    private SolutionWeatherContextService service;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        hoursRepository = mock(StoreBusinessHoursRepository.class);
        forecastService = mock(KmaVillageForecastService.class);
        summaryService = mock(WeatherContextSummaryService.class);
        persistenceService = mock(WeatherForecastSummaryPersistenceService.class);
        service = new SolutionWeatherContextService(storeRepository, hoursRepository,
                forecastService, summaryService, persistenceService,
                Clock.fixed(Instant.parse("2026-10-15T05:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void fetchesPersistsAndReturnsCurrentWeather() {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        WeatherContext weather = new WeatherContext("2026-10-15 11:00", "비", 14, 24,
                0.7, List.of(15), null, true);
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.of(weather));
        when(persistenceService.saveIfNewer(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.resolve(1L, TODAY)).contains(weather);
        verify(persistenceService).saveIfNewer(any(WeatherForecastSummaryEntity.class));
    }

    @Test
    void omitsWeatherWhenStoreHasNoCoordinates() {
        when(storeRepository.findById(1L)).thenReturn(Optional.of(mock(Store.class)));

        assertThat(service.resolve(1L, TODAY)).isEmpty();
        verify(forecastService, never()).fetch(any());
    }

    @Test
    void omitsWeatherWhenForecastFailsWithoutFailingGeneration() {
        givenCoordinates();
        when(forecastService.fetch(any(StoreCoordinates.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThat(service.resolve(1L, TODAY)).isEmpty();
        verify(persistenceService, never()).saveIfNewer(any());
    }

    @Test
    void omitsWeatherWhenSummaryIsUnavailable() {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.empty());

        assertThat(service.resolve(1L, TODAY)).isEmpty();
        verify(persistenceService, never()).saveIfNewer(any());
    }

    @Test
    void fetchesEarlierIssueOnlyWhenLatestForecastLacksTodaysDailyTemperatures() {
        givenCoordinates();
        KmaVillageForecastResponse latest = mock(KmaVillageForecastResponse.class);
        KmaVillageForecastResponse earlier = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(latest));
        when(summaryService.summarize(latest, List.of())).thenReturn(Optional.empty());
        when(summaryService.needsDailyTemperatureSupplement(latest, TODAY)).thenReturn(true);
        when(forecastService.fetchEarlyIssue(any(StoreCoordinates.class), org.mockito.Mockito.eq(TODAY)))
                .thenReturn(Optional.of(earlier));
        WeatherContext weather = new WeatherContext("2026-10-15 14:00", "구름많음", 11, 22,
                0.2, List.of(), null, false);
        when(summaryService.summarize(latest, earlier, List.of())).thenReturn(Optional.of(weather));
        when(persistenceService.saveIfNewer(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.resolve(1L, TODAY)).contains(weather);
        verify(persistenceService).saveIfNewer(any(WeatherForecastSummaryEntity.class));
    }

    @Test
    void omitsWeatherWhenEarlierIssueCannotSupplyTodaysDailyTemperatures() {
        givenCoordinates();
        KmaVillageForecastResponse latest = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(latest));
        when(summaryService.summarize(latest, List.of())).thenReturn(Optional.empty());
        when(summaryService.needsDailyTemperatureSupplement(latest, TODAY)).thenReturn(true);
        when(forecastService.fetchEarlyIssue(any(StoreCoordinates.class), org.mockito.Mockito.eq(TODAY)))
                .thenReturn(Optional.empty());

        assertThat(service.resolve(1L, TODAY)).isEmpty();
        verify(persistenceService, never()).saveIfNewer(any());
    }

    @Test
    void omitsWeatherWhenPersistenceFails() {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.of(
                new WeatherContext("2026-10-15 11:00", "비", 14, 24,
                        0.7, List.of(15), null, true)));
        when(persistenceService.saveIfNewer(any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(service.resolve(1L, TODAY)).isEmpty();
    }

    @Test
    void doesNotFetchWeatherForOtherDate() {
        assertThat(service.resolve(1L, TODAY.plusDays(1))).isEmpty();
        verify(storeRepository, never()).findById(any());
    }

    private void givenCoordinates() {
        Store store = mock(Store.class);
        when(store.getLatitude()).thenReturn(BigDecimal.valueOf(37.4786));
        when(store.getLongitude()).thenReturn(BigDecimal.valueOf(126.8646));
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(hoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(1L)).thenReturn(List.of());
    }
}
