package com.memme.service.solution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    private SalesSolutionGenerationService service;

    @BeforeEach
    void setUp() {
        contextResolver = mock(SalesSolutionGenerationContextResolver.class);
        persistenceService = mock(SalesSolutionPersistenceService.class);
        aiClient = mock(SalesSolutionAiClient.class);
        service = new SalesSolutionGenerationService(contextResolver, persistenceService, aiClient);
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
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(analysis.getId()).thenReturn(56L);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        when(contextResolver.resolve(1L, targetDate)).thenReturn(ready(run, analysis, metrics()));
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
