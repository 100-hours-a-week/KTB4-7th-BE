package com.memme.service.sales.analysis;

import java.time.LocalDate;
import java.time.YearMonth;

import com.memme.dto.sales.SalesAnalysisRetryResponse;
import com.memme.exception.sales.SalesAnalysisRetryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesAnalysisRetryServiceTest {

    private SalesAnalysisRetryLifecycleService lifecycleService;
    private SalesInsightRetryJob retryJob;
    private SalesAnalysisRetryService service;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(SalesAnalysisRetryLifecycleService.class);
        retryJob = mock(SalesInsightRetryJob.class);
        service = new SalesAnalysisRetryService(lifecycleService, retryJob);
    }

    @Test
    void acceptsInsightOnlyRetryAndReturnsNewAnalysisRun() {
        var reservation = reservation();
        when(lifecycleService.reserve(7L, 301L, 12L)).thenReturn(reservation);

        SalesAnalysisRetryResponse response = service.retry(7L, 301L, 12L);

        assertThat(response).isEqualTo(new SalesAnalysisRetryResponse(12L, 35L));
        verify(retryJob).start(reservation);
    }

    @Test
    void recordsNewRunAsFailedWhenAsyncJobCannotBeScheduled() {
        var reservation = reservation();
        when(lifecycleService.reserve(7L, 301L, 12L)).thenReturn(reservation);
        doThrow(new IllegalStateException("executor rejected"))
                .when(retryJob).start(reservation);

        assertThatThrownBy(() -> service.retry(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_FAILED);
                    assertThat(exception.getFailReason()).isEqualTo("INSIGHT_RETRY_FAILED");
                });

        verify(lifecycleService).fail(35L, "AI 인사이트 재시도 접수에 실패했습니다.");
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
