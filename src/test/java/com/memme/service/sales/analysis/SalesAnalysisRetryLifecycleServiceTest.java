package com.memme.service.sales.analysis;

import java.util.List;
import java.util.Optional;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesAnalysisRetryException;
import com.memme.repository.sales.AnalysisRunRepository;
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
    private StoreOwnershipRepository ownershipRepository;
    private SalesAnalysisRetryLifecycleService service;

    @BeforeEach
    void setUp() {
        uploadRepository = mock(SalesUploadRepository.class);
        runRepository = mock(AnalysisRunRepository.class);
        ownershipRepository = mock(StoreOwnershipRepository.class);
        service = new SalesAnalysisRetryLifecycleService(
                uploadRepository,
                runRepository,
                ownershipRepository
        );
    }

    @Test
    void reservesNewRunForRetryableFailedUpload() {
        SalesUploadEntity upload = failedUpload("UPLOAD_PROCESSING_ERROR");
        AnalysisRunEntity failedRun = mock(AnalysisRunEntity.class);
        AnalysisRunEntity retryRun = mock(AnalysisRunEntity.class);
        when(failedRun.getStatus()).thenReturn(AnalysisRunStatus.FAILED);
        when(retryRun.getId()).thenReturn(35L);
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(upload));
        when(runRepository.findFirstByBasedOnUploadIdOrderByIdDesc(12L))
                .thenReturn(Optional.of(failedRun));
        when(runRepository.saveAndFlush(any(AnalysisRunEntity.class))).thenReturn(retryRun);

        SalesAnalysisRetryLifecycleService.Reservation reservation = service.reserve(7L, 301L, 12L);

        assertThat(reservation).isEqualTo(
                new SalesAnalysisRetryLifecycleService.Reservation(12L, 301L, "301/file.xlsx", 35L)
        );
        verify(runRepository).existsByBasedOnUploadIdAndStatusIn(
                12L,
                List.of(AnalysisRunStatus.PENDING, AnalysisRunStatus.PROCESSING)
        );
    }

    @Test
    void rejectsSchemaFailureThatCannotChangeByRetryingSameFile() {
        SalesUploadEntity upload = failedUpload("PLATFORM_SCHEMA_MISMATCH");
        AnalysisRunEntity failedRun = mock(AnalysisRunEntity.class);
        when(failedRun.getStatus()).thenReturn(AnalysisRunStatus.FAILED);
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(upload));
        when(runRepository.findFirstByBasedOnUploadIdOrderByIdDesc(12L))
                .thenReturn(Optional.of(failedRun));

        assertThatThrownBy(() -> service.reserve(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_NOT_ALLOWED);
                    assertThat(exception.getFailReason()).isEqualTo("INVALID_SALES_SCHEMA");
                });
    }

    @Test
    void rejectsConcurrentRetryBeforeCreatingAnotherRun() {
        SalesUploadEntity upload = failedUpload("UPLOAD_PROCESSING_ERROR");
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(upload));
        when(runRepository.existsByBasedOnUploadIdAndStatusIn(
                12L,
                List.of(AnalysisRunStatus.PENDING, AnalysisRunStatus.PROCESSING)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.reserve(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_IN_PROGRESS));
    }

    private SalesUploadEntity failedUpload(String errorCode) {
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getId()).thenReturn(12L);
        when(upload.getStoreId()).thenReturn(301L);
        when(upload.getRequestedByUserId()).thenReturn(7L);
        when(upload.getStorageKey()).thenReturn("301/file.xlsx");
        when(upload.getStatus()).thenReturn(SalesUploadStatus.FAILED);
        when(upload.getErrorCode()).thenReturn(errorCode);
        return upload;
    }
}
