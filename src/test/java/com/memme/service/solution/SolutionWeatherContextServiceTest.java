package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionMetrics.WeatherContext;
import com.memme.dto.solution.external.KmaVillageForecastResponse;
import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.entity.store.Store;
import com.memme.exception.solution.ExternalDataLookupException;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.solution.external.KmaVillageForecastService;
import com.memme.service.solution.external.KmaWeatherAlert;
import com.memme.service.solution.external.KmaWeatherAlertService;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SolutionWeatherContextServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final String STORE_ADDRESS = "서울특별시 송파구 올림픽로 300";
    private StoreRepository storeRepository;
    private StoreBusinessHoursRepository hoursRepository;
    private KmaVillageForecastService forecastService;
    private KmaWeatherAlertService alertService;
    private WeatherContextSummaryService summaryService;
    private WeatherForecastSummaryPersistenceService persistenceService;
    private SolutionWeatherContextService service;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        hoursRepository = mock(StoreBusinessHoursRepository.class);
        forecastService = mock(KmaVillageForecastService.class);
        alertService = mock(KmaWeatherAlertService.class);
        summaryService = mock(WeatherContextSummaryService.class);
        persistenceService = mock(WeatherForecastSummaryPersistenceService.class);
        service = new SolutionWeatherContextService(storeRepository, hoursRepository,
                forecastService, alertService, summaryService, persistenceService,
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
        when(alertService.findActiveLandAlerts(STORE_ADDRESS)).thenReturn(List.of());

        assertThat(service.resolve(1L, TODAY)).contains(withAlerts(weather, List.of()));
        verify(persistenceService).saveIfNewer(any(WeatherForecastSummaryEntity.class));
    }

    @Test
    void activeAlertsAreAddedAndMakeWeatherNotableWithoutChangingPersistedForecastSummary() {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        WeatherContext forecastWeather = new WeatherContext("2026-10-15 11:00", "맑음", 14, 24,
                0.0, List.of(), null, false);
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.of(forecastWeather));
        when(persistenceService.saveIfNewer(any())).thenAnswer(invocation -> invocation.getArgument(0));
        KmaWeatherAlert alert = new KmaWeatherAlert("강풍주의보", "L1100100", TODAY.atTime(10, 0));
        when(alertService.findActiveLandAlerts(STORE_ADDRESS)).thenReturn(List.of(alert, alert));

        WeatherContext resolved = service.resolve(1L, TODAY).orElseThrow();

        assertThat(resolved.isNotable()).isTrue();
        assertThat(resolved.alerts()).containsExactly("강풍주의보");
        ArgumentCaptor<WeatherForecastSummaryEntity> persisted = ArgumentCaptor.forClass(
                WeatherForecastSummaryEntity.class);
        verify(persistenceService).saveIfNewer(persisted.capture());
        assertThat(persisted.getValue().isNotable()).isFalse();
    }

    @Test
    void successfulAlertLookupWithoutAlertsSendsEmptyArrayAndPreservesForecastNotable() {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        WeatherContext forecastWeather = new WeatherContext("2026-10-15 11:00", "맑음", 14, 24,
                0.0, List.of(), null, false);
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.of(forecastWeather));
        when(persistenceService.saveIfNewer(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(alertService.findActiveLandAlerts(STORE_ADDRESS)).thenReturn(List.of());

        WeatherContext resolved = service.resolve(1L, TODAY).orElseThrow();

        assertThat(resolved.isNotable()).isFalse();
        assertThat(resolved.alerts()).isEmpty();
    }

    @Test
    void lookupPathReturnsForecastOnlyWithoutQueryingAlerts() {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        WeatherContext forecastWeather = new WeatherContext("2026-10-15 11:00", "맑음", 14, 24,
                0.0, List.of(), null, false);
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.of(forecastWeather));
        when(persistenceService.saveIfNewer(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WeatherContext resolved = service.resolveForLookup(1L, TODAY).orElseThrow();

        assertThat(resolved).isEqualTo(forecastWeather);
        verify(alertService, never()).findActiveLandAlerts(any());
    }

    @Test
    void alertLookupFailureOmitsAlertsPreservesForecastAndLogsOnlySafeReason(CapturedOutput output) {
        givenCoordinates();
        KmaVillageForecastResponse response = mock(KmaVillageForecastResponse.class);
        when(forecastService.fetch(any(StoreCoordinates.class))).thenReturn(Optional.of(response));
        WeatherContext forecastWeather = new WeatherContext("2026-10-15 11:00", "맑음", 14, 24,
                0.0, List.of(), null, false);
        when(summaryService.summarize(response, List.of())).thenReturn(Optional.of(forecastWeather));
        when(persistenceService.saveIfNewer(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(alertService.findActiveLandAlerts(STORE_ADDRESS))
                .thenThrow(new ExternalDataLookupException("KMA_ALERT_AUTH_ERROR"));

        WeatherContext resolved = service.resolve(1L, TODAY).orElseThrow();

        assertThat(resolved.isNotable()).isFalse();
        assertThat(resolved.alerts()).isNull();
        assertThat(output.getOut())
                .contains("reason=KMA_ALERT_AUTH_ERROR")
                .doesNotContain("authKey", "encoded%2Bkey%3D");
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
        when(alertService.findActiveLandAlerts(STORE_ADDRESS)).thenReturn(List.of());

        assertThat(service.resolve(1L, TODAY)).contains(withAlerts(weather, List.of()));
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
        when(store.getAddress()).thenReturn(STORE_ADDRESS);
        when(store.getLatitude()).thenReturn(BigDecimal.valueOf(37.4786));
        when(store.getLongitude()).thenReturn(BigDecimal.valueOf(126.8646));
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(hoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(1L)).thenReturn(List.of());
    }

    private WeatherContext withAlerts(WeatherContext weather, List<String> alerts) {
        return new WeatherContext(weather.basisDateTime(), weather.skyText(), weather.minTempC(), weather.maxTempC(),
                weather.maxPop(), weather.rainHours(), weather.precipitationMm(), weather.isNotable(), alerts);
    }
}
