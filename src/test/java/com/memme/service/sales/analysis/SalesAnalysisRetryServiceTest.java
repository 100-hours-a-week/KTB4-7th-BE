package com.memme.service.sales.analysis;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import com.memme.dto.sales.SalesAnalysisRetryResponse;
import com.memme.exception.sales.SalesAnalysisRetryException;
import com.memme.service.sales.TossPosWorkbookData;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.storage.SalesFileStorage;
import com.memme.service.sales.upload.SalesUploadPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesAnalysisRetryServiceTest {

    private SalesAnalysisRetryLifecycleService lifecycleService;
    private SalesFileStorage fileStorage;
    private TossPosWorkbookParser workbookParser;
    private SalesUploadPersistenceService uploadPersistenceService;
    private SalesAnalysisService analysisService;
    private SalesAnalysisSnapshotService snapshotService;
    private SalesAnalysisRetryService service;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(SalesAnalysisRetryLifecycleService.class);
        fileStorage = mock(SalesFileStorage.class);
        workbookParser = mock(TossPosWorkbookParser.class);
        uploadPersistenceService = mock(SalesUploadPersistenceService.class);
        analysisService = mock(SalesAnalysisService.class);
        snapshotService = mock(SalesAnalysisSnapshotService.class);
        service = new SalesAnalysisRetryService(
                lifecycleService,
                fileStorage,
                workbookParser,
                uploadPersistenceService,
                analysisService,
                snapshotService
        );
    }

    @Test
    void retriesStoredUploadWithNewAnalysisRun() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 7);
        TossPosWorkbookData data = new TossPosWorkbookData(start, end, List.of(), List.of(), List.of());
        SalesAnalysisResult analysis = new SalesAnalysisResult.Empty(null);
        when(lifecycleService.reserve(7L, 301L, 12L)).thenReturn(
                new SalesAnalysisRetryLifecycleService.Reservation(12L, 301L, "301/file.xlsx", 35L)
        );
        when(fileStorage.load("301/file.xlsx")).thenReturn(new byte[]{1});
        when(workbookParser.parse(any(InputStream.class))).thenReturn(data);
        when(analysisService.analyze(301L, "CUSTOM", start, end)).thenReturn(analysis);
        when(snapshotService.persistForRun(35L, analysis)).thenReturn(57L);

        SalesAnalysisRetryResponse response = service.retry(7L, 301L, 12L);

        assertThat(response).isEqualTo(new SalesAnalysisRetryResponse(12L, 35L, 57L));
        verify(uploadPersistenceService).replaceCoverage(12L, 301L, data);
        verify(lifecycleService).start(35L, start, end);
        verify(lifecycleService).complete(35L);
    }

    @Test
    void recordsNewRunAsFailedWithoutChangingOriginalUpload() throws Exception {
        when(lifecycleService.reserve(7L, 301L, 12L)).thenReturn(
                new SalesAnalysisRetryLifecycleService.Reservation(12L, 301L, "301/file.xlsx", 35L)
        );
        when(fileStorage.load("301/file.xlsx")).thenReturn(new byte[]{1});
        when(workbookParser.parse(any(InputStream.class)))
                .thenThrow(new IllegalStateException("parser failed"));

        assertThatThrownBy(() -> service.retry(7L, 301L, 12L))
                .isInstanceOfSatisfying(SalesAnalysisRetryException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(SalesAnalysisRetryException.Reason.RETRY_FAILED);
                    assertThat(exception.getFailReason()).isEqualTo("ANALYSIS_RETRY_FAILED");
                });

        verify(lifecycleService).fail(35L, "매출 분석 재시도 중 오류가 발생했습니다.");
    }
}
