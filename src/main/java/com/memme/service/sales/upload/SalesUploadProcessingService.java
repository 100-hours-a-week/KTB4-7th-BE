package com.memme.service.sales.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.YearMonth;

import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadProcessingPhase;
import com.memme.exception.sales.SalesUploadProcessingException;
import com.memme.exception.sales.SalesUploadRequestException;
import com.memme.exception.sales.TossPosWorkbookValidationException;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.sales.TossPosWorkbookData;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.analysis.SalesAnalysisService;
import com.memme.service.sales.analysis.SalesAnalysisResult;
import com.memme.service.sales.analysis.SalesAnalysisSnapshotService;
import com.memme.service.sales.insight.SalesInsightGenerationService;
import com.memme.service.sales.storage.SalesFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SalesUploadProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SalesUploadProcessingService.class);

    private final SalesUploadRepository uploadRepository;
    private final SalesFileStorage fileStorage;
    private final TossPosWorkbookParser workbookParser;
    private final SalesUploadLifecycleService lifecycleService;
    private final SalesUploadPersistenceService persistenceService;
    private final SalesAnalysisService analysisService;
    private final SalesAnalysisSnapshotService snapshotService;
    private final SalesInsightGenerationService insightGenerationService;
    private final SalesAiPostProcessingJob aiPostProcessingJob;

    public SalesUploadProcessingService(SalesUploadRepository uploadRepository,
                                        SalesFileStorage fileStorage,
                                        TossPosWorkbookParser workbookParser,
                                        SalesUploadLifecycleService lifecycleService,
                                        SalesUploadPersistenceService persistenceService,
                                        SalesAnalysisService analysisService,
                                        SalesAnalysisSnapshotService snapshotService,
                                        SalesInsightGenerationService insightGenerationService,
                                        SalesAiPostProcessingJob aiPostProcessingJob) {
        this.uploadRepository = uploadRepository;
        this.fileStorage = fileStorage;
        this.workbookParser = workbookParser;
        this.lifecycleService = lifecycleService;
        this.persistenceService = persistenceService;
        this.analysisService = analysisService;
        this.snapshotService = snapshotService;
        this.insightGenerationService = insightGenerationService;
        this.aiPostProcessingJob = aiPostProcessingJob;
    }

    public SalesUploadResult process(Long uploadId) {
        try {
            SalesUploadEntity upload = findUpload(uploadId);
            lifecycleService.startProcessing(uploadId);
            byte[] content = fileStorage.load(upload.getStorageKey());
            TossPosWorkbookData data = workbookParser.parse(new ByteArrayInputStream(content));

            lifecycleService.advancePhase(uploadId, SalesUploadProcessingPhase.NORMALIZING);
            lifecycleService.updateCoverage(uploadId, data.periodStart(), data.periodEnd(), data.items().size());
            lifecycleService.advancePhase(uploadId, SalesUploadProcessingPhase.AGGREGATING);
            SalesUploadResult result = persistenceService.replaceCoverage(uploadId, upload.getStoreId(), data);
            lifecycleService.advancePhase(uploadId, SalesUploadProcessingPhase.ANALYZING);
            SalesAnalysisResult analysis = analysisService.analyze(
                    upload.getStoreId(),
                    "CUSTOM",
                    data.periodStart(),
                    data.periodEnd()
            );
            Long salesAnalysisId = snapshotService.persist(uploadId, analysis);
            startSalesInsightGeneration(
                    uploadId,
                    upload.getStoreId(),
                    salesAnalysisId,
                    data
            );
            lifecycleService.markCompleted(uploadId, result.appliedRecordCount());
            startAiPostProcessing(upload, uploadId, data);
            return result;
        } catch (TossPosWorkbookValidationException exception) {
            recordFailure(uploadId, "PLATFORM_SCHEMA_MISMATCH", exception.getMessage(), exception);
            throw new SalesUploadRequestException(
                    "PLATFORM_SCHEMA_MISMATCH",
                    exception.getMessage()
            );
        } catch (IOException | UncheckedIOException exception) {
            SalesUploadProcessingException wrapped = new SalesUploadProcessingException(
                    "매출 파일을 읽지 못했습니다.",
                    exception
            );
            recordFailure(uploadId, "FILE_READ_ERROR", wrapped.getMessage(), wrapped);
            throw wrapped;
        } catch (RuntimeException exception) {
            recordFailure(uploadId, "UPLOAD_PROCESSING_ERROR", "업로드 처리 중 오류가 발생했습니다.", exception);
            throw new SalesUploadProcessingException("업로드 처리 중 오류가 발생했습니다.", exception);
        }
    }

    private void startSalesInsightGeneration(
            Long uploadId,
            Long storeId,
            Long salesAnalysisId,
            TossPosWorkbookData data
    ) {
        try {
            insightGenerationService.generate(
                    storeId,
                    salesAnalysisId,
                    lifecycleService.analysisRunId(uploadId),
                    YearMonth.from(data.periodEnd()),
                    SalesInsightTriggerType.UPLOAD
            );
        } catch (RuntimeException exception) {
            log.warn("Sales insight generation failed for uploadId={}", uploadId, exception);
        }
    }

    private void startAiPostProcessing(
            SalesUploadEntity upload,
            Long uploadId,
            TossPosWorkbookData data
    ) {
        try {
            aiPostProcessingJob.start(
                    upload.getStoreId(),
                    uploadId,
                    lifecycleService.analysisRunId(uploadId),
                    data.periodEnd().plusDays(1)
            );
        } catch (RuntimeException exception) {
            log.warn("AI post processing could not be scheduled for uploadId={}", uploadId, exception);
        }
    }

    private SalesUploadEntity findUpload(Long uploadId) {
        return uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalStateException("매출 업로드를 찾을 수 없습니다: " + uploadId));
    }

    private void recordFailure(Long uploadId, String reason, String message, RuntimeException original) {
        try {
            lifecycleService.markFailed(uploadId, reason, message);
        } catch (RuntimeException statusException) {
            original.addSuppressed(statusException);
        }
    }
}
