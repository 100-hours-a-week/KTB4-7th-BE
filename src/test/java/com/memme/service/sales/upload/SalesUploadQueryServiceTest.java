package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.memme.dto.sales.SalesUploadHistoryRequest;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadProcessingPhase;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesUploadQueryException;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesUploadQueryServiceTest {

    private SalesUploadRepository uploadRepository;
    private SalesOrderItemRepository salesOrderItemRepository;
    private AnalysisRunRepository analysisRunRepository;
    private SalesAiInsightRepository insightRepository;
    private StoreOwnershipRepository storeOwnershipRepository;
    private SalesUploadQueryService service;
    private SalesAnalysisRepository salesAnalysisRepository;

    @BeforeEach
    void setUp() {
        uploadRepository = mock(SalesUploadRepository.class);
        salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        analysisRunRepository = mock(AnalysisRunRepository.class);
        insightRepository = mock(SalesAiInsightRepository.class);
        storeOwnershipRepository = mock(StoreOwnershipRepository.class);
        salesAnalysisRepository = mock(SalesAnalysisRepository.class);
        service = new SalesUploadQueryService(
                uploadRepository,
                salesOrderItemRepository,
                analysisRunRepository,
                insightRepository,
                storeOwnershipRepository,
                salesAnalysisRepository
        );
    }

    @Test
    void returnsMonthlyHistoryAndConnectionSummary() {
        long userId = 7L;
        long storeId = 301L;
        YearMonth month = YearMonth.of(2026, 9);
        SalesUploadEntity upload = upload(12L, storeId, SalesUploadStatus.COMPLETED);
        PageRequest pageable = PageRequest.of(0, 10);
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(storeId, userId)).thenReturn(true);
        when(uploadRepository.findOverlappingPeriod(
                storeId, month.atDay(1), month.atEndOfMonth(), pageable
        )).thenReturn(new PageImpl<>(List.of(upload), pageable, 1));
        when(uploadRepository.findFirstByStoreIdOrderByUploadedAtDesc(storeId))
                .thenReturn(Optional.of(upload));
        when(salesOrderItemRepository.countCurrentItemsByStoreId(storeId)).thenReturn(31L);

        var response = service.getHistory(
                userId,
                storeId,
                new SalesUploadHistoryRequest(month, 1, 10)
        );

        assertThat(response.connection().latestStatus()).isEqualTo("COMPLETED");
        assertThat(response.connection().totalAppliedRecordCount()).isEqualTo(31L);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.uploadId()).isEqualTo(12L);
            assertThat(item.fileName()).isEqualTo("sales.xlsx");
            assertThat(item.recordCount()).isEqualTo(20L);
            assertThat(item.appliedRecordCount()).isEqualTo(18L);
        });
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalCount()).isEqualTo(1L);
        verify(salesOrderItemRepository).countCurrentItemsByStoreId(storeId);
    }

    @Test
    void returnsProcessingStatusWithAnalysisRun() {
        long userId = 7L;
        long storeId = 301L;
        SalesUploadEntity upload = upload(12L, storeId, SalesUploadStatus.PROCESSING);
        when(upload.getProcessingPhase()).thenReturn(SalesUploadProcessingPhase.ANALYZING);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        when(run.getId()).thenReturn(34L);
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(storeId, userId)).thenReturn(true);
        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(analysisRunRepository.findFirstByBasedOnUploadIdOrderByIdDesc(12L))
                .thenReturn(Optional.of(run));

        var response = service.getStatus(userId, storeId, 12L);

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.data().analysisRunId()).isEqualTo(34L);
        assertThat(response.data().processingPhase()).isEqualTo("ANALYZING");
        assertThat(response.data().aiInsightStatus()).isEqualTo("PENDING");
        assertThat(response.data().retryable()).isFalse();
    }

    @Test
    void marksCompletedUploadRetryableWhenMonthlyInsightFailed() {
        long userId = 7L;
        long storeId = 301L;
        SalesUploadEntity upload = upload(12L, storeId, SalesUploadStatus.COMPLETED);
        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesAiInsightEntity insight = mock(SalesAiInsightEntity.class);
        when(run.getId()).thenReturn(34L);
        when(insight.getStatus()).thenReturn(SalesAiInsightStatus.FAILED);
        when(insight.getSalesAnalysisId()).thenReturn(51L);
        SalesAnalysisEntity analysis = mock(SalesAnalysisEntity.class);
        when(analysis.getId()).thenReturn(51L);
        when(salesAnalysisRepository.findByAnalysisRunId(34L)).thenReturn(Optional.of(analysis));
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(storeId, userId)).thenReturn(true);
        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(analysisRunRepository.findFirstByBasedOnUploadIdOrderByIdDesc(12L))
                .thenReturn(Optional.of(run));
        when(insightRepository.findByStoreIdAndTargetMonth(storeId, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));

        var response = service.getStatus(userId, storeId, 12L);

        assertThat(response.data().retryable()).isTrue();
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.failReason()).isEqualTo("AI_INSIGHT_ERROR");
        assertThat(response.data().processingPhase()).isNull();
        assertThat(response.data().aiInsightStatus()).isEqualTo("FAILED");
    }

    @Test
    void keepsOriginalAnalysisIdWhenLatestRunIsAnInsightRetry() {
        var upload = upload(12L, 301L, SalesUploadStatus.COMPLETED);
        var retryRun = mock(AnalysisRunEntity.class);
        var originalRun = mock(AnalysisRunEntity.class);
        var analysis = mock(SalesAnalysisEntity.class);
        var insight = mock(SalesAiInsightEntity.class);
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(analysisRunRepository.findFirstByBasedOnUploadIdOrderByIdDesc(12L)).thenReturn(Optional.of(retryRun));
        when(retryRun.getId()).thenReturn(35L);
        when(insightRepository.findByStoreIdAndTargetMonth(301L, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));
        when(insight.getStatus()).thenReturn(SalesAiInsightStatus.GENERATING);
        when(insight.getSalesAnalysisId()).thenReturn(51L);
        when(salesAnalysisRepository.findById(51L)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(51L);
        when(analysis.getAnalysisRunId()).thenReturn(34L);
        when(analysisRunRepository.findById(34L)).thenReturn(Optional.of(originalRun));
        when(originalRun.getBasedOnUploadId()).thenReturn(12L);

        var response = service.getStatus(7L, 301L, 12L);

        assertThat(response.data().analysisRunId()).isEqualTo(35L);
        assertThat(response.data().analysisId()).isEqualTo(51L);
        assertThat(response.data().aiInsightStatus()).isEqualTo("GENERATING");
        assertThat(response.data().retryable()).isFalse();
    }

    @Test
    void doesNotReturnAnotherUploadsAnalysisForTheSameMonth() {
        var upload = upload(12L, 301L, SalesUploadStatus.COMPLETED);
        var run = mock(AnalysisRunEntity.class);
        var previousRun = mock(AnalysisRunEntity.class);
        var analysis = mock(SalesAnalysisEntity.class);
        var insight = mock(SalesAiInsightEntity.class);
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(analysisRunRepository.findFirstByBasedOnUploadIdOrderByIdDesc(12L)).thenReturn(Optional.of(run));
        when(run.getId()).thenReturn(34L);
        when(insightRepository.findByStoreIdAndTargetMonth(301L, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));
        when(insight.getStatus()).thenReturn(SalesAiInsightStatus.FAILED);
        when(insight.getSalesAnalysisId()).thenReturn(40L);
        when(salesAnalysisRepository.findById(40L)).thenReturn(Optional.of(analysis));
        when(analysis.getAnalysisRunId()).thenReturn(30L);
        when(analysisRunRepository.findById(30L)).thenReturn(Optional.of(previousRun));
        when(previousRun.getBasedOnUploadId()).thenReturn(11L);

        var response = service.getStatus(7L, 301L, 12L);

        assertThat(response.data().analysisId()).isNull();
        assertThat(response.data().retryable()).isFalse();
        assertThat(response.failReason()).isNull();
    }

    @Test
    void rejectsUserWhoDoesNotOwnActiveStore() {
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(301L, 8L)).thenReturn(false);

        assertThatThrownBy(() -> service.getStatus(8L, 301L, 12L))
                .isInstanceOfSatisfying(SalesUploadQueryException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(SalesUploadQueryException.Reason.STORE_OWNER_REQUIRED));
    }

    @Test
    void hidesUploadOwnedByAnotherStore() {
        SalesUploadEntity anotherStoreUpload = upload(
                12L,
                999L,
                SalesUploadStatus.COMPLETED
        );
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(uploadRepository.findById(12L))
                .thenReturn(Optional.of(anotherStoreUpload));

        assertThatThrownBy(() -> service.getStatus(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesUploadQueryException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(SalesUploadQueryException.Reason.UPLOAD_NOT_FOUND));
    }

    private SalesUploadEntity upload(Long id, Long storeId, SalesUploadStatus status) {
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getId()).thenReturn(id);
        when(upload.getStoreId()).thenReturn(storeId);
        when(upload.getOriginalFileName()).thenReturn("sales.xlsx");
        when(upload.getUploadedAt()).thenReturn(LocalDateTime.of(2026, 9, 21, 12, 30));
        when(upload.getPeriodStart()).thenReturn(LocalDate.of(2026, 9, 1));
        when(upload.getPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));
        when(upload.getTotalRowCount()).thenReturn(20L);
        when(upload.getAppliedRecordCount()).thenReturn(18L);
        when(upload.getStatus()).thenReturn(status);
        return upload;
    }
}
