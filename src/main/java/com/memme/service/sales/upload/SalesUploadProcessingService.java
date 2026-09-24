package com.memme.service.sales.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadProcessingPhase;
import com.memme.exception.sales.SalesUploadProcessingException;
import com.memme.exception.sales.SalesUploadRequestException;
import com.memme.exception.sales.TossPosWorkbookValidationException;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.sales.TossPosWorkbookData;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.analysis.SalesAnalysisService;
import com.memme.service.sales.storage.SalesFileStorage;
import org.springframework.stereotype.Service;

@Service
public class SalesUploadProcessingService {

    private final SalesUploadRepository uploadRepository;
    private final SalesFileStorage fileStorage;
    private final TossPosWorkbookParser workbookParser;
    private final SalesUploadLifecycleService lifecycleService;
    private final SalesUploadPersistenceService persistenceService;
    private final SalesAnalysisService analysisService;

    public SalesUploadProcessingService(SalesUploadRepository uploadRepository,
                                        SalesFileStorage fileStorage,
                                        TossPosWorkbookParser workbookParser,
                                        SalesUploadLifecycleService lifecycleService,
                                        SalesUploadPersistenceService persistenceService,
                                        SalesAnalysisService analysisService) {
        this.uploadRepository = uploadRepository;
        this.fileStorage = fileStorage;
        this.workbookParser = workbookParser;
        this.lifecycleService = lifecycleService;
        this.persistenceService = persistenceService;
        this.analysisService = analysisService;
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
            analysisService.analyze(upload.getStoreId(), "CUSTOM", data.periodStart(), data.periodEnd());
            lifecycleService.markCompleted(uploadId, result.appliedRecordCount());
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
