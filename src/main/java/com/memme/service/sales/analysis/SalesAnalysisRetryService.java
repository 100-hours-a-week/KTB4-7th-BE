package com.memme.service.sales.analysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.memme.dto.sales.SalesAnalysisRetryResponse;
import com.memme.exception.sales.SalesAnalysisRetryException;
import com.memme.service.sales.TossPosWorkbookData;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.storage.SalesFileStorage;
import com.memme.service.sales.upload.SalesUploadPersistenceService;
import org.springframework.stereotype.Service;

import static com.memme.exception.sales.SalesAnalysisRetryException.Reason.RETRY_FAILED;

@Service
public class SalesAnalysisRetryService {

    private final SalesAnalysisRetryLifecycleService lifecycleService;
    private final SalesFileStorage fileStorage;
    private final TossPosWorkbookParser workbookParser;
    private final SalesUploadPersistenceService uploadPersistenceService;
    private final SalesAnalysisService analysisService;
    private final SalesAnalysisSnapshotService snapshotService;

    public SalesAnalysisRetryService(
            SalesAnalysisRetryLifecycleService lifecycleService,
            SalesFileStorage fileStorage,
            TossPosWorkbookParser workbookParser,
            SalesUploadPersistenceService uploadPersistenceService,
            SalesAnalysisService analysisService,
            SalesAnalysisSnapshotService snapshotService
    ) {
        this.lifecycleService = lifecycleService;
        this.fileStorage = fileStorage;
        this.workbookParser = workbookParser;
        this.uploadPersistenceService = uploadPersistenceService;
        this.analysisService = analysisService;
        this.snapshotService = snapshotService;
    }

    public SalesAnalysisRetryResponse retry(Long userId, Long storeId, Long uploadId) {
        SalesAnalysisRetryLifecycleService.Reservation reservation =
                lifecycleService.reserve(userId, storeId, uploadId);
        try {
            byte[] content = fileStorage.load(reservation.storageKey());
            TossPosWorkbookData data = workbookParser.parse(new ByteArrayInputStream(content));
            lifecycleService.start(
                    reservation.analysisRunId(),
                    data.periodStart(),
                    data.periodEnd()
            );
            uploadPersistenceService.replaceCoverage(
                    reservation.uploadId(),
                    reservation.storeId(),
                    data
            );
            SalesAnalysisResult result = analysisService.analyze(
                    reservation.storeId(),
                    "CUSTOM",
                    data.periodStart(),
                    data.periodEnd()
            );
            Long analysisId = snapshotService.persistForRun(reservation.analysisRunId(), result);
            lifecycleService.complete(reservation.analysisRunId());
            return new SalesAnalysisRetryResponse(
                    reservation.uploadId(),
                    reservation.analysisRunId(),
                    analysisId
            );
        } catch (IOException exception) {
            recordFailure(reservation.analysisRunId(), exception);
            throw new SalesAnalysisRetryException(
                    RETRY_FAILED,
                    "ANALYSIS_RETRY_FAILED",
                    exception
            );
        } catch (RuntimeException exception) {
            recordFailure(reservation.analysisRunId(), exception);
            throw new SalesAnalysisRetryException(
                    RETRY_FAILED,
                    "ANALYSIS_RETRY_FAILED",
                    exception
            );
        }
    }

    private void recordFailure(Long analysisRunId, RuntimeException exception) {
        try {
            lifecycleService.fail(analysisRunId, "매출 분석 재시도 중 오류가 발생했습니다.");
        } catch (RuntimeException statusException) {
            exception.addSuppressed(statusException);
        }
    }

    private void recordFailure(Long analysisRunId, IOException exception) {
        try {
            lifecycleService.fail(analysisRunId, "매출 분석 재시도 중 오류가 발생했습니다.");
        } catch (RuntimeException statusException) {
            exception.addSuppressed(statusException);
        }
    }
}
