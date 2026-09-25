package com.memme.service.sales.analysis;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesAnalysisRetryException;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesAnalysisRetryLifecycleServiceTest {

    private SalesUploadRepository uploadRepository;
    private AnalysisRunRepository runRepository;
    private SalesAiInsightRepository insightRepository;
    private StoreOwnershipRepository ownershipRepository;
    private SalesAnalysisRetryLifecycleService service;

    @BeforeEach
    void setUp() {
        uploadRepository = mock(SalesUploadRepository.class);
        runRepository = mock(AnalysisRunRepository.class);
        insightRepository = mock(SalesAiInsightRepository.class);
        ownershipRepository = mock(StoreOwnershipRepository.class);
        service = new SalesAnalysisRetryLifecycleService(
                uploadRepository,
                runRepository,
                insightRepository,
                ownershipRepository
        );
    }

    @Test
    void reservesNewRunForFailedInsightWithoutReprocessingUpload() {
        SalesUploadEntity upload = completedUpload();
        SalesAiInsightEntity insight = insight(SalesAiInsightStatus.FAILED);
        AnalysisRunEntity retryRun = mock(AnalysisRunEntity.class);
        when(retryRun.getId()).thenReturn(35L);
        givenAccessibleUpload(upload);
        when(insightRepository.findByStoreIdAndTargetMonth(301L, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));
        when(runRepository.saveAndFlush(any(AnalysisRunEntity.class))).thenReturn(retryRun);

        var reservation = service.reserve(7L, 301L, 12L);

        assertThat(reservation).isEqualTo(new SalesAnalysisRetryLifecycleService.Reservation(
                12L, 301L, 56L, 35L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                YearMonth.of(2026, 9)
        ));
        verify(runRepository).existsByBasedOnUploadIdAndStatusIn(
                12L,
                List.of(AnalysisRunStatus.PENDING, AnalysisRunStatus.PROCESSING)
        );
    }

    @Test
    void rejectsUploadThatDidNotCompleteAggregation() {
        SalesUploadEntity upload = completedUpload();
        when(upload.getStatus()).thenReturn(SalesUploadStatus.FAILED);
        givenAccessibleUpload(upload);

        assertNotRetryable();
    }

    @Test
    void rejectsWhenFailedInsightDoesNotExist() {
        givenAccessibleUpload(completedUpload());
        when(insightRepository.findByStoreIdAndTargetMonth(301L, YearMonth.of(2026, 9)))
                .thenReturn(Optional.empty());

        assertNotRetryable();
    }

    @Test
    void rejectsConcurrentAnalysisRunBeforeCreatingAnotherRun() {
        SalesUploadEntity upload = completedUpload();
        givenAccessibleUpload(upload);
        when(runRepository.existsByBasedOnUploadIdAndStatusIn(
                12L,
                List.of(AnalysisRunStatus.PENDING, AnalysisRunStatus.PROCESSING)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.reserve(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_IN_PROGRESS));
    }

    @Test
    void rejectsInsightThatIsAlreadyGenerating() {
        givenAccessibleUpload(completedUpload());
        SalesAiInsightEntity generatingInsight = insight(SalesAiInsightStatus.GENERATING);
        when(insightRepository.findByStoreIdAndTargetMonth(301L, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(generatingInsight));

        assertThatThrownBy(() -> service.reserve(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_IN_PROGRESS));
    }

    private void assertNotRetryable() {
        assertThatThrownBy(() -> service.reserve(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_NOT_ALLOWED);
                    assertThat(exception.getFailReason()).isEqualTo("INVALID_SALES_SCHEMA");
                });
    }

    private void givenAccessibleUpload(SalesUploadEntity upload) {
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(upload));
    }

    private SalesUploadEntity completedUpload() {
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getId()).thenReturn(12L);
        when(upload.getStoreId()).thenReturn(301L);
        when(upload.getRequestedByUserId()).thenReturn(7L);
        when(upload.getStatus()).thenReturn(SalesUploadStatus.COMPLETED);
        when(upload.getPeriodStart()).thenReturn(LocalDate.of(2026, 9, 1));
        when(upload.getPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));
        return upload;
    }

    private SalesAiInsightEntity insight(SalesAiInsightStatus status) {
        SalesAiInsightEntity insight = mock(SalesAiInsightEntity.class);
        when(insight.getSalesAnalysisId()).thenReturn(56L);
        when(insight.getStatus()).thenReturn(status);
        return insight;
    }
}
