package com.memme.service.solution;

import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.repository.solution.WeatherForecastSummaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherForecastSummaryPersistenceServiceTest {

    private WeatherForecastSummaryRepository repository;
    private WeatherForecastSummaryPersistenceService service;

    @BeforeEach
    void setUp() {
        repository = mock(WeatherForecastSummaryRepository.class);
        service = new WeatherForecastSummaryPersistenceService(repository);
    }

    @Test
    void insertsFirstSummary() {
        WeatherForecastSummaryEntity incoming = summary("2026-10-15T11:00");
        when(repository.saveAndFlush(incoming)).thenReturn(incoming);

        assertThat(service.saveIfNewer(incoming)).isSameAs(incoming);
        verify(repository).saveAndFlush(incoming);
    }

    @Test
    void retainsExistingSummaryWhenIncomingIsNotNewer() {
        WeatherForecastSummaryEntity incoming = summary("2026-10-15T08:00");
        WeatherForecastSummaryEntity existing = summary("2026-10-15T11:00");
        when(repository.findByStoreIdAndTargetDate(1L, LocalDate.of(2026, 10, 15)))
                .thenReturn(Optional.of(existing));

        assertThat(service.saveIfNewer(incoming)).isSameAs(existing);
        verify(repository, never()).saveAndFlush(incoming);
    }

    @Test
    void readsUpdatedSummaryAfterConditionalUpdate() {
        WeatherForecastSummaryEntity incoming = summary("2026-10-15T14:00");
        when(repository.updateIfNewer(incoming)).thenReturn(1);
        when(repository.findByStoreIdAndTargetDate(1L, LocalDate.of(2026, 10, 15)))
                .thenReturn(Optional.of(incoming));

        assertThat(service.saveIfNewer(incoming)).isSameAs(incoming);
        verify(repository, never()).saveAndFlush(incoming);
    }

    private WeatherForecastSummaryEntity summary(String basis) {
        return WeatherForecastSummaryEntity.create(1L, LocalDate.of(2026, 10, 15),
                LocalDateTime.parse(basis), "비", 14, 24, BigDecimal.valueOf(0.7),
                List.of(15), null, true, LocalDateTime.of(2026, 10, 15, 14, 0));
    }
}
