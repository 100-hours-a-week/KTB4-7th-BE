package com.memme.service.solution;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.memme.dto.sales.MenuRankingMetric;
import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.exception.solution.SalesSolutionAiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesSolutionGenerationServiceTest {

    private SalesSolutionGenerationContextResolver contextResolver;
    private SalesSolutionPersistenceService persistenceService;
    private SalesSolutionAiClient aiClient;
    private SolutionWeatherContextService weatherContextService;
    private SalesSolutionGenerationService service;

    @BeforeEach
    void setUp() {
        contextResolver = mock(SalesSolutionGenerationContextResolver.class);
        persistenceService = mock(SalesSolutionPersistenceService.class);
        aiClient = mock(SalesSolutionAiClient.class);
        weatherContextService = mock(SolutionWeatherContextService.class);
        service = new SalesSolutionGenerationService(
                contextResolver,
                persistenceService,
                aiClient,
                weatherContextService
        );
    }

    @Test
    void generatesAndPersistsCardsForLatestAnalysis() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(analysis.getId()).thenReturn(56L);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesSolutionMetrics metrics = metrics();
        when(contextResolver.resolve(1L, targetDate)).thenReturn(ready(run, analysis, metrics));
        when(contextResolver.latestSalesAnalysisId(1L)).thenReturn(Optional.of(56L));
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(77L);
        when(persistenceService.start(1L, 56L, targetDate))
                .thenReturn(new SalesSolutionPersistenceService.StartResult(bundle, true));
        SalesSolutionGenerationResponse response = response(targetDate);
        when(aiClient.generate(any(SalesSolutionGenerationRequest.class))).thenReturn(response);

        SalesSolutionGenerationResult result = service.generateAfterUpload(1L, targetDate);

        assertThat(result.status()).isEqualTo(SalesSolutionGenerationResult.Status.COMPLETED);
        assertThat(result.solutionBundleId()).isEqualTo(77L);
        verify(persistenceService).complete(77L, 56L, response.data());
    }

    @Test
    void includesWeatherWhenAvailable() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        givenGenerationContext(targetDate);
        when(contextResolver.latestSalesAnalysisId(1L)).thenReturn(Optional.of(56L));
        when(weatherContextService.resolve(1L, targetDate)).thenReturn(Optional.of(
                new SalesSolutionMetrics.WeatherContext("2026-09-24 11:00", "비", 14, 24,
                        0.7, List.of(15), null, true)));
        when(aiClient.generate(any())).thenReturn(response(targetDate));

        assertThat(service.generateScheduled(1L, targetDate).status())
                .isEqualTo(SalesSolutionGenerationResult.Status.COMPLETED);
        org.mockito.ArgumentCaptor<SalesSolutionGenerationRequest> request =
                org.mockito.ArgumentCaptor.forClass(SalesSolutionGenerationRequest.class);
        verify(aiClient).generate(request.capture());
        assertThat(request.getValue().metrics().weatherContext().maxPop()).isEqualTo(0.7);
    }

    @Test
    void preservesMenuRankingsAndBusinessHoursInAiRequest() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        MenuRankingMetric ranking = new MenuRankingMetric(
                1, "아이스 아메리카노", BigDecimal.valueOf(310_420), 91L,
                BigDecimal.valueOf(0.261), BigDecimal.valueOf(0.031));
        SalesSolutionMetrics.BusinessHours businessHours = new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("08:00", "22:00"),
                new SalesSolutionMetrics.BusinessHourBand("09:00", "21:00"),
                List.of("MON"));
        DistrictBenchmarkMetric benchmark = new DistrictBenchmarkMetric(
                "2026Q2", 16_887_880, 159, "11-14", "17-21");
        givenGenerationContext(targetDate, new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), 1_100_000L, null, null,
                List.of(ranking), businessHours, null, benchmark));
        when(contextResolver.latestSalesAnalysisId(1L)).thenReturn(Optional.of(56L));
        when(aiClient.generate(any())).thenReturn(response(targetDate));

        assertThat(service.generateScheduled(1L, targetDate).status())
                .isEqualTo(SalesSolutionGenerationResult.Status.COMPLETED);
        org.mockito.ArgumentCaptor<SalesSolutionGenerationRequest> request =
                org.mockito.ArgumentCaptor.forClass(SalesSolutionGenerationRequest.class);
        verify(aiClient).generate(request.capture());
        assertThat(request.getValue().metrics().menuRankings()).containsExactly(ranking);
        assertThat(request.getValue().metrics().businessHours()).isEqualTo(businessHours);
        assertThat(request.getValue().metrics().districtBenchmark()).isEqualTo(benchmark);
    }

    @Test
    void forwardsConditionPatternsToAiSolutionRequest() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        SalesSolutionMetrics.ConditionPattern conditionPattern = new SalesSolutionMetrics.ConditionPattern(
                "RAIN", 12, 1.0, 9, 38, 0.67, 0.71,
                List.of(new SalesSolutionMetrics.ConditionMenu(
                        "아이스 아메리카노", 84, 112, -0.25, 294_000, 392_000)));
        givenGenerationContext(targetDate, new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), 1_100_000L, null, null, null, null, List.of(conditionPattern)));
        when(contextResolver.latestSalesAnalysisId(1L)).thenReturn(Optional.of(56L));
        when(aiClient.generate(any())).thenReturn(response(targetDate));

        assertThat(service.generateScheduled(1L, targetDate).status())
                .isEqualTo(SalesSolutionGenerationResult.Status.COMPLETED);
        org.mockito.ArgumentCaptor<SalesSolutionGenerationRequest> request =
                org.mockito.ArgumentCaptor.forClass(SalesSolutionGenerationRequest.class);
        verify(aiClient).generate(request.capture());
        assertThat(request.getValue().metrics().conditionPatterns()).containsExactly(conditionPattern);
    }

    @Test
    void continuesWithoutWeatherWhenUnavailable() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        givenGenerationContext(targetDate);
        when(contextResolver.latestSalesAnalysisId(1L)).thenReturn(Optional.of(56L));
        when(weatherContextService.resolve(1L, targetDate)).thenReturn(Optional.empty());
        when(aiClient.generate(any())).thenReturn(response(targetDate));

        assertThat(service.generateScheduled(1L, targetDate).status())
                .isEqualTo(SalesSolutionGenerationResult.Status.COMPLETED);
        org.mockito.ArgumentCaptor<SalesSolutionGenerationRequest> request =
                org.mockito.ArgumentCaptor.forClass(SalesSolutionGenerationRequest.class);
        verify(aiClient).generate(request.capture());
        assertThat(request.getValue().metrics().weatherContext()).isNull();
    }

    @Test
    void doesNotCallAiWithoutForecast() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        when(contextResolver.resolve(1L, targetDate)).thenReturn(
                new SalesSolutionGenerationContextResolver.Resolution(
                        SalesSolutionGenerationContextResolver.Availability.INSUFFICIENT_HISTORY,
                        null
                )
        );

        SalesSolutionGenerationResult result = service.generateAfterUpload(1L, targetDate);

        assertThat(result.status())
                .isEqualTo(SalesSolutionGenerationResult.Status.INSUFFICIENT_HISTORY);
        verify(aiClient, never()).generate(any());
        verify(persistenceService, never()).start(any(), any(), any());
    }

    @Test
    void doesNotGenerateDuplicateCardsForExistingCompletedBundle() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(analysis.getId()).thenReturn(56L);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        when(contextResolver.resolve(1L, targetDate)).thenReturn(ready(run, analysis, metrics()));
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(77L);
        when(bundle.getStatus()).thenReturn(SolutionBundleStatus.COMPLETED);
        when(persistenceService.start(1L, 56L, targetDate))
                .thenReturn(new SalesSolutionPersistenceService.StartResult(bundle, false));

        SalesSolutionGenerationResult result = service.generateAfterUpload(1L, targetDate);

        assertThat(result.status()).isEqualTo(SalesSolutionGenerationResult.Status.COMPLETED);
        assertThat(result.solutionBundleId()).isEqualTo(77L);
        verify(aiClient, never()).generate(any());
        verify(persistenceService, never()).complete(any(), any(), any());
    }

    @Test
    void failsBundleWithoutSavingCardsWhenAiResponseIsInvalid() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        givenGenerationContext(targetDate);
        when(aiClient.generate(any())).thenThrow(new SalesSolutionAiException(
                422,
                "INVALID_AI_RESPONSE",
                "솔루션 AI 응답이 올바르지 않습니다.",
                false
        ));

        SalesSolutionGenerationResult result = service.generateAfterUpload(1L, targetDate);

        assertThat(result.status()).isEqualTo(SalesSolutionGenerationResult.Status.FAILED);
        assertThat(result.retryable()).isFalse();
        verify(persistenceService).fail(77L);
        verify(persistenceService, never()).complete(any(), any(), any());
        verify(aiClient).generate(any());
    }

    @Test
    void retriesRetryableAiFailureOnceAndPreservesFailureState() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        givenGenerationContext(targetDate);
        when(aiClient.generate(any())).thenThrow(new SalesSolutionAiException(
                503,
                "AI_UNAVAILABLE",
                "AI 서버를 사용할 수 없습니다.",
                true
        ));

        SalesSolutionGenerationResult result = service.generateAfterUpload(1L, targetDate);

        assertThat(result.status()).isEqualTo(SalesSolutionGenerationResult.Status.FAILED);
        assertThat(result.retryable()).isTrue();
        verify(aiClient, times(2)).generate(any());
        verify(persistenceService).fail(77L);
        verify(persistenceService, never()).complete(any(), any(), any());
    }

    @Test
    void rejectsResponseForAnotherTargetDateWithoutSavingCards() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        givenGenerationContext(targetDate);
        when(aiClient.generate(any())).thenReturn(response(targetDate.plusDays(1)));

        SalesSolutionGenerationResult result = service.generateAfterUpload(1L, targetDate);

        assertThat(result.status()).isEqualTo(SalesSolutionGenerationResult.Status.FAILED);
        verify(persistenceService).fail(77L);
        verify(persistenceService, never()).complete(any(), any(), any());
    }

    private void givenGenerationContext(LocalDate targetDate) {
        givenGenerationContext(targetDate, metrics());
    }

    private void givenGenerationContext(LocalDate targetDate, SalesSolutionMetrics metrics) {
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(analysis.getId()).thenReturn(56L);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        when(contextResolver.resolve(1L, targetDate)).thenReturn(ready(run, analysis, metrics));
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(77L);
        when(persistenceService.start(1L, 56L, targetDate))
                .thenReturn(new SalesSolutionPersistenceService.StartResult(bundle, true));
    }

    private SalesSolutionGenerationContextResolver.Resolution ready(
            AnalysisRunEntity run,
            SalesAnalysisEntity analysis,
            SalesSolutionMetrics metrics
    ) {
        return new SalesSolutionGenerationContextResolver.Resolution(
                SalesSolutionGenerationContextResolver.Availability.READY,
                new SalesSolutionGenerationContext(run, analysis, metrics)
        );
    }

    private SalesSolutionMetrics metrics() {
        return new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(),
                List.of(),
                1_100_000L,
                null
        );
    }

    private SalesSolutionGenerationResponse response(LocalDate targetDate) {
        return new SalesSolutionGenerationResponse(
                "솔루션을 생성했습니다.",
                null,
                new SalesSolutionGenerationResponse.Data(
                        targetDate,
                        List.of(new SalesSolutionGenerationResponse.SolutionCard(
                                1,
                                "프로모션 진행",
                                "요약",
                                "상세",
                                "근거"
                        )),
                        "claude-sonnet-4-5"
                ),
                null
        );
    }
}
