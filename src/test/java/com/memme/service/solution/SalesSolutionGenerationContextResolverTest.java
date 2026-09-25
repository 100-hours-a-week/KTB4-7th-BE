package com.memme.service.solution;

import java.time.LocalDate;
import java.util.Optional;

import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesForecastRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesSolutionGenerationContextResolverTest {

    private AnalysisRunRepository runRepository;
    private SalesAnalysisRepository analysisRepository;
    private SalesForecastRepository forecastRepository;
    private SalesDailySummaryRepository dailySummaryRepository;
    private SalesSolutionMetricsAssembler metricsAssembler;
    private SalesSolutionGenerationContextResolver resolver;

    @BeforeEach
    void setUp() {
        runRepository = mock(AnalysisRunRepository.class);
        analysisRepository = mock(SalesAnalysisRepository.class);
        forecastRepository = mock(SalesForecastRepository.class);
        dailySummaryRepository = mock(SalesDailySummaryRepository.class);
        metricsAssembler = mock(SalesSolutionMetricsAssembler.class);
        resolver = new SalesSolutionGenerationContextResolver(
                runRepository,
                analysisRepository,
                forecastRepository,
                dailySummaryRepository,
                metricsAssembler
        );
    }

    @Test
    void usesOnlyLatestCompletedAnalysisForRequestedStore() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability())
                .isEqualTo(SalesSolutionGenerationContextResolver.Availability.EMPTY);
        verify(runRepository).findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L,
                AnalysisRunStatus.COMPLETED
        );
        verify(analysisRepository, never()).findByAnalysisRunId(org.mockito.ArgumentMatchers.any());
        verify(forecastRepository, never()).findByStoreIdAndTargetDate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void returnsEmptyWhenCompletedRunHasNoAnalysisSnapshot() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        when(run.getId()).thenReturn(34L);
        when(runRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L, AnalysisRunStatus.COMPLETED
        )).thenReturn(Optional.of(run));
        when(analysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.empty());

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability())
                .isEqualTo(SalesSolutionGenerationContextResolver.Availability.EMPTY);
        verify(forecastRepository, never()).findByStoreIdAndTargetDate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void assemblesReadyContextFromSameStoreRunAndForecast() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        SalesSolutionMetrics metrics = mock(SalesSolutionMetrics.class);
        when(run.getId()).thenReturn(34L);
        when(runRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L, AnalysisRunStatus.COMPLETED
        )).thenReturn(Optional.of(run));
        when(analysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.of(analysis));
        when(forecastRepository.findByStoreIdAndTargetDate(10L, targetDate))
                .thenReturn(Optional.of(forecast));
        when(metricsAssembler.assemble(10L, run, forecast)).thenReturn(metrics);

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability())
                .isEqualTo(SalesSolutionGenerationContextResolver.Availability.READY);
        assertThat(result.context().analysisRun()).isSameAs(run);
        assertThat(result.context().salesAnalysis()).isSameAs(analysis);
        assertThat(result.context().metrics()).isSameAs(metrics);
    }
}
