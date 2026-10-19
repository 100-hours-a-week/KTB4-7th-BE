package com.memme.service.chat;

import com.memme.dto.chat.ChatLookupResponse;
import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.repository.solution.WeatherForecastSummaryRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.sales.insight.DistrictBenchmarkService;
import com.memme.service.solution.SolutionWeatherContextService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChatSalesLookupServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private StoreRepository stores;
    private WeatherForecastSummaryRepository forecasts;
    private SolutionWeatherContextService weather;
    private DistrictBenchmarkService districts;
    private ChatSalesLookupService service;

    @BeforeEach
    void setUp() {
        stores = mock(StoreRepository.class);
        forecasts = mock(WeatherForecastSummaryRepository.class);
        weather = mock(SolutionWeatherContextService.class);
        districts = mock(DistrictBenchmarkService.class);
        service = new ChatSalesLookupService(stores, forecasts, weather, districts,
                Clock.fixed(Instant.parse("2026-10-18T15:00:00Z"), ZoneId.of("UTC")));
        when(stores.existsById(1L)).thenReturn(true);
    }

    @Test
    void returnsStoredWeatherWithoutFetchingAgain() {
        var saved = WeatherForecastSummaryEntity.create(1L, TODAY,
                LocalDateTime.of(2026, 10, 19, 11, 0), "비", 12, 19,
                new BigDecimal("0.600"), List.of(17), null, true,
                LocalDateTime.of(2026, 10, 19, 12, 0));
        when(forecasts.findByStoreIdAndTargetDate(1L, TODAY)).thenReturn(Optional.of(saved));

        var result = service.weather(1L, null);

        assertThat(result.status()).isEqualTo(ChatLookupResponse.Status.READY);
        assertThat(result.data().basisDateTime()).isEqualTo("2026-10-19 11:00");
        assertThat(result.data().rainHours()).containsExactly(17);
        verifyNoInteractions(weather);
    }

    @Test
    void returnsEmptyForUnavailablePastWeather() {
        LocalDate past = TODAY.minusDays(1);
        when(weather.resolveForLookup(1L, past)).thenReturn(Optional.empty());

        var result = service.weather(1L, past);

        assertThat(result.status()).isEqualTo(ChatLookupResponse.Status.EMPTY);
        assertThat(result.data()).isNull();
    }

    @Test
    void doesNotTurnProviderFailureIntoEmpty() {
        when(weather.resolveForLookup(1L, TODAY)).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.weather(1L, TODAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void returnsCurrentDistrictBenchmark() {
        when(districts.findForInsight(1L, YearMonth.of(2026, 10)))
                .thenReturn(Optional.of(new DistrictBenchmarkMetric("2026Q3", 1000000, 5,
                        "14-17", "17-21")));

        var result = service.districtBenchmark(1L);

        assertThat(result.status()).isEqualTo(ChatLookupResponse.Status.READY);
        assertThat(result.data().basisQuarter()).isEqualTo("2026Q3");
    }

    @Test
    void returnsEmptyForMissingDistrictBenchmark() {
        when(districts.findForInsight(1L, YearMonth.of(2026, 10))).thenReturn(Optional.empty());

        assertThat(service.districtBenchmark(1L).status()).isEqualTo(ChatLookupResponse.Status.EMPTY);
    }

    @Test
    void missingStoreIsNotAnEmptyLookup() {
        when(stores.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.weather(2L, TODAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verifyNoInteractions(forecasts, weather);
    }
}
