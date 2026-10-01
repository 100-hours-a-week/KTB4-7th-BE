package com.memme.service.solution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.entity.sales.SalesDailySummaryEntity;
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
        List<SalesDailySummaryEntity> sufficientHistory = summariesBetween(
                LocalDate.of(2026, 6, 24),
                LocalDate.of(2026, 9, 23)
        );
        SalesSolutionMetrics metrics = mock(SalesSolutionMetrics.class);
        when(run.getId()).thenReturn(34L);
        when(runRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L, AnalysisRunStatus.COMPLETED
        )).thenReturn(Optional.of(run));
        when(analysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.of(analysis));
        when(forecastRepository.findByStoreIdAndTargetDate(10L, targetDate))
                .thenReturn(Optional.of(forecast));
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L))
                .thenReturn(sufficientHistory);
        when(metricsAssembler.assemble(10L, run, forecast)).thenReturn(metrics);

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability())
                .isEqualTo(SalesSolutionGenerationContextResolver.Availability.READY);
        assertThat(result.context().analysisRun()).isSameAs(run);
        assertThat(result.context().salesAnalysis()).isSameAs(analysis);
        assertThat(result.context().metrics()).isSameAs(metrics);
    }

    @Test
    void rejectsForecastWhenLatestContinuousSalesHistoryIsShorterThanThreeMonths() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        List<SalesDailySummaryEntity> shortHistory = summariesBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 27)
        );
        when(run.getId()).thenReturn(34L);
        when(runRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L, AnalysisRunStatus.COMPLETED
        )).thenReturn(Optional.of(run));
        when(analysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.of(analysis));
        when(forecastRepository.findByStoreIdAndTargetDate(10L, targetDate))
                .thenReturn(Optional.of(forecast));
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L))
                .thenReturn(shortHistory);

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability())
                .isEqualTo(SalesSolutionGenerationContextResolver.Availability.INSUFFICIENT_HISTORY);
        verify(metricsAssembler, never()).assemble(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void treatsAGapBeforeTheLatestDateAsTheStartOfANewHistoryPeriod() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        List<SalesDailySummaryEntity> historyBeforeGap = summariesBetween(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 8, 31)
        );
        List<SalesDailySummaryEntity> historyAfterGap = summariesBetween(
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 27)
        );
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L))
                .thenReturn(java.util.stream.Stream.concat(historyBeforeGap.stream(), historyAfterGap.stream()).toList());

        var coverage = resolver.historyCoverage(10L, targetDate);

        assertThat(coverage).isEqualTo(SalesSolutionGenerationContextResolver.HistoryCoverage.INSUFFICIENT);
    }

    @Test
    void classifiesLatestContinuousHistoryEvenWhenItDoesNotEndYesterday() {
        LocalDate targetDate = LocalDate.of(2026, 11, 1);
        List<SalesDailySummaryEntity> history = summariesBetween(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30)
        );
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L)).thenReturn(history);

        var coverage = resolver.historyCoverage(10L, targetDate);

        assertThat(coverage).isEqualTo(SalesSolutionGenerationContextResolver.HistoryCoverage.LIMITED);
    }

    @Test
    void identifiesSalesOutsideTheThirtyFiveDayForecastWindow() {
        LocalDate targetDate = LocalDate.of(2026, 10, 1);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(run.getId()).thenReturn(34L);
        when(runRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L, AnalysisRunStatus.COMPLETED
        )).thenReturn(Optional.of(run));
        when(analysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.of(analysis));
        List<SalesDailySummaryEntity> history = summariesBetween(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L)).thenReturn(history);

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability().name()).isEqualTo("FORECAST_OUT_OF_RANGE");
    }

    @Test
    void keepsForecastPendingAtTheThirtyFiveDayBoundary() {
        LocalDate targetDate = LocalDate.of(2026, 10, 1);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(run.getId()).thenReturn(34L);
        when(runRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                10L, AnalysisRunStatus.COMPLETED
        )).thenReturn(Optional.of(run));
        when(analysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.of(analysis));
        List<SalesDailySummaryEntity> history = summariesBetween(
                LocalDate.of(2026, 5, 27), LocalDate.of(2026, 8, 27));
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L)).thenReturn(history);

        var result = resolver.resolve(10L, targetDate);

        assertThat(result.availability())
                .isEqualTo(SalesSolutionGenerationContextResolver.Availability.FORECAST_PENDING);
    }

    @Test
    void classifiesFourToSeptemberTwentySixContinuousHistoryAsLimitedOnSeptemberTwentyEighth() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        List<SalesDailySummaryEntity> history = summariesBetween(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 9, 26)
        );
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L)).thenReturn(history);

        var coverage = resolver.historyCoverage(10L, targetDate);

        assertThat(coverage).isEqualTo(SalesSolutionGenerationContextResolver.HistoryCoverage.LIMITED);
    }

    @Test
    void classifiesThreeToTwelveMonthsOfContinuousHistoryAsLimited() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        List<SalesDailySummaryEntity> history = summariesBetween(
                LocalDate.of(2026, 6, 28),
                LocalDate.of(2026, 9, 27)
        );
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L)).thenReturn(history);

        var coverage = resolver.historyCoverage(10L, targetDate);

        assertThat(coverage).isEqualTo(SalesSolutionGenerationContextResolver.HistoryCoverage.LIMITED);
    }

    @Test
    void classifiesAtLeastOneYearOfContinuousHistoryAsSufficient() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        List<SalesDailySummaryEntity> history = summariesBetween(
                LocalDate.of(2025, 9, 28),
                LocalDate.of(2026, 9, 27)
        );
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(10L)).thenReturn(history);

        var coverage = resolver.historyCoverage(10L, targetDate);

        assertThat(coverage).isEqualTo(SalesSolutionGenerationContextResolver.HistoryCoverage.SUFFICIENT);
    }

    private List<SalesDailySummaryEntity> summariesBetween(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    SalesDailySummaryEntity summary = mock(SalesDailySummaryEntity.class);
                    when(summary.getSalesDate()).thenReturn(date);
                    return summary;
                })
                .toList();
    }
}
