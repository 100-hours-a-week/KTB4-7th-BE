package com.memme.service.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.dto.sales.SalesSolutionGenerationStatus;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.solution.SolutionBundleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SolutionRegenerationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 21);

    @Test
    void skipsCompletedSolutionWithoutRainPattern() {
        var fixture = new Fixture();
        fixture.prepare(List.of());

        assertThat(fixture.service.regenerate(1L, DATE)).isEqualTo(SolutionRegenerationOutcome.SKIPPED);
        verify(fixture.client, never()).generate(any());
    }

    @Test
    void replacesExistingCardsOnlyAfterFreshCompletedCandidate() {
        var fixture = new Fixture();
        var pattern = new SalesSolutionMetrics.ConditionPattern("RAIN", 12, 1.0, 9, 38,
                0.67, 0.71, List.of());
        fixture.prepare(List.of(pattern));
        var reservation = SolutionRegenerationReservation.acquired(8L, 1, LocalDateTime.now(fixture.clock));
        when(fixture.fingerprints.of(any(), any(), any())).thenReturn("b".repeat(64));
        when(fixture.persistence.reserve(eq(1L), eq(DATE), eq(10L), eq("b".repeat(64)), any()))
                .thenReturn(reservation);
        var data = new SalesSolutionGenerationResponse.Data(DATE,
                List.of(new SalesSolutionGenerationResponse.SolutionCard(1, "new", "summary", "detail", null)),
                "model");
        when(fixture.client.generate(any(SalesSolutionGenerationRequest.class)))
                .thenReturn(new SalesSolutionGenerationResponse("ok", SalesSolutionGenerationStatus.COMPLETED,
                        data, null));

        assertThat(fixture.service.regenerate(1L, DATE)).isEqualTo(SolutionRegenerationOutcome.COMPLETED);
        verify(fixture.persistence).complete(eq(1L), eq(DATE), eq(10L), eq(11L),
                eq(reservation), eq(data), any());
    }

    @Test
    void changedWeatherDuringAiCallKeepsPreviousSolution() {
        var fixture = new Fixture();
        var pattern = new SalesSolutionMetrics.ConditionPattern("RAIN", 12, 1.0, 9, 38,
                0.67, 0.71, List.of());
        fixture.prepare(List.of(pattern));
        var reservation = SolutionRegenerationReservation.acquired(9L, 1, LocalDateTime.now(fixture.clock));
        when(fixture.fingerprints.of(any(), any(), any()))
                .thenReturn("a".repeat(64), "b".repeat(64));
        when(fixture.persistence.reserve(eq(1L), eq(DATE), eq(10L), eq("a".repeat(64)), any()))
                .thenReturn(reservation);
        var newWeather = new SalesSolutionMetrics.WeatherContext(
                "2026-10-21 12:00", "흐림", 10, 15, 0.8, List.of(16), null, true);
        when(fixture.weather.resolve(1L, DATE))
                .thenReturn(Optional.empty(), Optional.of(newWeather));
        var data = new SalesSolutionGenerationResponse.Data(DATE,
                List.of(new SalesSolutionGenerationResponse.SolutionCard(1, "new", "summary", "detail", null)),
                "model");
        when(fixture.client.generate(any(SalesSolutionGenerationRequest.class)))
                .thenReturn(new SalesSolutionGenerationResponse("ok", SalesSolutionGenerationStatus.COMPLETED,
                        data, null));

        assertThat(fixture.service.regenerate(1L, DATE)).isEqualTo(SolutionRegenerationOutcome.FAILED);
        verify(fixture.persistence).fail(eq(reservation), any());
        verify(fixture.persistence, never()).complete(any(), any(), any(), any(), any(), any(), any());
    }

    private static final class Fixture {
        private final SolutionBundleRepository bundles = mock(SolutionBundleRepository.class);
        private final SalesSolutionGenerationContextResolver contexts = mock(SalesSolutionGenerationContextResolver.class);
        private final SalesSolutionAiClient client = mock(SalesSolutionAiClient.class);
        private final SolutionWeatherContextService weather = mock(SolutionWeatherContextService.class);
        private final SolutionRegenerationPersistenceService persistence = mock(SolutionRegenerationPersistenceService.class);
        private final SolutionRegenerationSourceFingerprint fingerprints = mock(SolutionRegenerationSourceFingerprint.class);
        private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        private final SolutionRegenerationService service = new SolutionRegenerationService(
                bundles, contexts, client, weather, persistence, fingerprints, clock);

        private void prepare(List<SalesSolutionMetrics.ConditionPattern> patterns) {
            var bundle = mock(SolutionBundleEntity.class);
            when(bundle.getStatus()).thenReturn(SolutionBundleStatus.COMPLETED);
            when(bundle.getSalesAnalysisId()).thenReturn(10L);
            when(bundles.findByStoreIdAndTargetDate(1L, DATE)).thenReturn(Optional.of(bundle));
            var analysis = mock(SalesAnalysisEntity.class);
            when(analysis.getId()).thenReturn(11L);
            var metrics = new SalesSolutionMetrics(new SalesSolutionMetrics.SalesSummary(100L, null),
                    List.of(), List.of(), null, null, null, null, null, patterns);
            var context = new SalesSolutionGenerationContext(mock(AnalysisRunEntity.class), analysis, metrics);
            when(contexts.resolve(1L, DATE)).thenReturn(new SalesSolutionGenerationContextResolver.Resolution(
                    SalesSolutionGenerationContextResolver.Availability.READY, context));
            when(weather.resolve(1L, DATE)).thenReturn(Optional.empty());
        }
    }
}
