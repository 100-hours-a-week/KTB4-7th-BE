package com.memme.service.sales.upload;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadProcessingPhase;
import com.memme.exception.sales.SalesUploadRequestException;
import com.memme.exception.sales.TossPosWorkbookValidationException;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.sales.TossPosWorkbookData;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.analysis.SalesAnalysisService;
import com.memme.exception.sales.SalesUploadProcessingException;
import com.memme.service.sales.analysis.SalesAnalysisSnapshotService;
import com.memme.service.sales.storage.SalesFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesUploadProcessingServiceTest {

    private SalesUploadRepository uploadRepository;
    private SalesFileStorage fileStorage;
    private TossPosWorkbookParser workbookParser;
    private SalesUploadLifecycleService lifecycleService;
    private SalesUploadPersistenceService persistenceService;
    private SalesUploadProcessingService service;
    private SalesAnalysisService analysisService;
    private SalesAnalysisSnapshotService snapshotService;
    private SalesAiPostProcessingJob aiPostProcessingJob;

    @BeforeEach
    void setUp() {
        uploadRepository = mock(SalesUploadRepository.class);
        fileStorage = mock(SalesFileStorage.class);
        workbookParser = mock(TossPosWorkbookParser.class);
        lifecycleService = mock(SalesUploadLifecycleService.class);
        persistenceService = mock(SalesUploadPersistenceService.class);
        analysisService = mock(SalesAnalysisService.class);
        snapshotService = mock(SalesAnalysisSnapshotService.class);
        aiPostProcessingJob = mock(SalesAiPostProcessingJob.class);
        service = new SalesUploadProcessingService(
                uploadRepository,
                fileStorage,
                workbookParser,
                lifecycleService,
                persistenceService,
                analysisService,
                snapshotService,
                aiPostProcessingJob
        );
    }

    @Test
    void parsesStoredFilePersistsResultAndCompletesUploadHistory() throws Exception {
        LocalDate periodStart = LocalDate.of(2026, 9, 1);
        LocalDate periodEnd = LocalDate.of(2026, 9, 7);
        SalesUploadEntity upload = pendingUpload();
        TossPosWorkbookData workbookData = new TossPosWorkbookData(
                periodStart,
                periodEnd,
                List.of(),
                List.of(),
                List.of()
        );
        SalesUploadResult expected = new SalesUploadResult(
                12L,
                periodStart,
                periodEnd,
                0,
                0
        );

        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(fileStorage.load(upload.getStorageKey())).thenReturn(new byte[]{1, 2, 3});
        when(workbookParser.parse(any(InputStream.class))).thenReturn(workbookData);
        when(persistenceService.replaceCoverage(12L, 301L, workbookData)).thenReturn(expected);

        assertThat(service.process(12L)).isEqualTo(expected);

        InOrder lifecycle = inOrder(lifecycleService, analysisService);
        lifecycle.verify(lifecycleService).startProcessing(12L);
        lifecycle.verify(lifecycleService).advancePhase(12L, SalesUploadProcessingPhase.NORMALIZING);
        lifecycle.verify(lifecycleService).updateCoverage(12L, periodStart, periodEnd, 0);
        lifecycle.verify(lifecycleService).advancePhase(12L, SalesUploadProcessingPhase.AGGREGATING);
        lifecycle.verify(lifecycleService).advancePhase(12L, SalesUploadProcessingPhase.ANALYZING);
        lifecycle.verify(analysisService).analyze(301L, "CUSTOM", periodStart, periodEnd);
        lifecycle.verify(lifecycleService).markCompleted(12L, 0);
    }

    @Test
    void recordsFailedHistoryAndReturnsImmediateErrorWhenParsingFails() throws Exception {
        SalesUploadEntity upload = pendingUpload();
        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(fileStorage.load(upload.getStorageKey())).thenReturn(new byte[]{1, 2, 3});
        when(workbookParser.parse(any(InputStream.class)))
                .thenThrow(new TossPosWorkbookValidationException("필수 시트가 없습니다."));

        assertThatThrownBy(() -> service.process(12L))
                .isInstanceOfSatisfying(SalesUploadRequestException.class, exception -> {
                    assertThat(exception.getFailReason()).isEqualTo("PLATFORM_SCHEMA_MISMATCH");
                    assertThat(exception).hasMessage("필수 시트가 없습니다.");
                });

        verify(lifecycleService).markFailed(
                12L,
                "PLATFORM_SCHEMA_MISMATCH",
                "필수 시트가 없습니다."
        );
        verify(persistenceService, never()).replaceCoverage(any(), any(), any());
    }

    @Test
    void analysisFailureNeverMarksRunCompleted() throws Exception {
        var date = LocalDate.of(2026, 9, 1);
        var data = new TossPosWorkbookData(date, date, List.of(), List.of(), List.of());
        var upload = pendingUpload();
        when(uploadRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(fileStorage.load(upload.getStorageKey())).thenReturn(new byte[]{1});
        when(workbookParser.parse(any(InputStream.class))).thenReturn(data);
        when(persistenceService.replaceCoverage(12L, 301L, data))
                .thenReturn(new SalesUploadResult(12L, date, date, 0, 0));
        when(analysisService.analyze(301L, "CUSTOM", date, date))
                .thenThrow(new IllegalStateException("analysis failed"));
        assertThatThrownBy(() -> service.process(12L)).isInstanceOf(SalesUploadProcessingException.class);
        verify(lifecycleService, never()).markCompleted(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(lifecycleService).markFailed(12L, "UPLOAD_PROCESSING_ERROR", "업로드 처리 중 오류가 발생했습니다.");
    }

    private SalesUploadEntity pendingUpload() {
        return SalesUploadEntity.pending(
                301L,
                7L,
                "sales.xlsx",
                "301/ab/file.xlsx",
                "a".repeat(64)
        );
    }
}
