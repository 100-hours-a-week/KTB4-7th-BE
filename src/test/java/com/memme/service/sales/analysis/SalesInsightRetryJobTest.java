package com.memme.service.sales.analysis;

import java.time.LocalDate;
import java.time.YearMonth;

import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.service.sales.insight.SalesInsightGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesInsightRetryJobTest {

    private SalesAnalysisRetryLifecycleService lifecycleService;
    private SalesInsightGenerationService generationService;
    private SalesInsightRetryJob job;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(SalesAnalysisRetryLifecycleService.class);
        generationService = mock(SalesInsightGenerationService.class);
        job = new SalesInsightRetryJob(lifecycleService, generationService);
    }

    @Test
    void reusesStoredAnalysisMetricsAndCompletesNewRun() {
        var reservation = reservation();
        whenGenerate(reservation, SalesAiInsightStatus.COMPLETED);

        job.start(reservation);

        verify(lifecycleService).start(
                35L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        verify(generationService).generate(
                301L,
                56L,
                35L,
                YearMonth.of(2026, 9),
                SalesInsightTriggerType.RETRY
        );
        verify(lifecycleService).complete(35L);
    }

    @Test
    void marksNewRunFailedWhenAiReturnsFailed() {
        var reservation = reservation();
        whenGenerate(reservation, SalesAiInsightStatus.FAILED);

        job.start(reservation);

        verify(lifecycleService).fail(35L, "AI 인사이트 재시도에 실패했습니다.");
    }

    @Test
    void marksNewRunFailedWhenGenerationThrows() {
        var reservation = reservation();
        when(generationService.generate(
                301L, 56L, 35L, YearMonth.of(2026, 9), SalesInsightTriggerType.RETRY
        )).thenThrow(new IllegalStateException("unexpected"));

        job.start(reservation);

        verify(lifecycleService).fail(35L, "AI 인사이트 재시도에 실패했습니다.");
    }

    private void whenGenerate(
            SalesAnalysisRetryLifecycleService.Reservation reservation,
            SalesAiInsightStatus status
    ) {
        when(generationService.generate(
                reservation.storeId(),
                reservation.salesAnalysisId(),
                reservation.analysisRunId(),
                reservation.targetMonth(),
                SalesInsightTriggerType.RETRY
        )).thenReturn(status);
    }

    private SalesAnalysisRetryLifecycleService.Reservation reservation() {
        return new SalesAnalysisRetryLifecycleService.Reservation(
                12L,
                301L,
                56L,
                35L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                YearMonth.of(2026, 9)
        );
    }
}
