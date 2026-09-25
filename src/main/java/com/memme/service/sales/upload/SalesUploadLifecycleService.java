package com.memme.service.sales.upload;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesUploadProcessingPhase;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesUploadLifecycleService {

    private final SalesUploadRepository uploadRepository;
    private final AnalysisRunRepository analysisRunRepository;

    public SalesUploadLifecycleService(SalesUploadRepository uploadRepository,
                                       AnalysisRunRepository analysisRunRepository) {
        this.uploadRepository = uploadRepository;
        this.analysisRunRepository = analysisRunRepository;
    }

    @Transactional
    public SalesUploadReceipt createPending(
            Long storeId,
            Long requestedByUserId,
            String originalFileName,
            String storageKey,
            String checksum
    ) {
        SalesUploadEntity upload = SalesUploadEntity.pending(
                storeId,
                requestedByUserId,
                originalFileName,
                storageKey,
                checksum
        );
        uploadRepository.saveAndFlush(upload);
        AnalysisRunEntity run = analysisRunRepository.saveAndFlush(
                AnalysisRunEntity.pending(storeId, requestedByUserId, upload.getId())
        );
        return new SalesUploadReceipt(upload.getId(), run.getId(), "PENDING");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startProcessing(Long uploadId) {
        findUpload(uploadId).startProcessing();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCoverage(Long uploadId, java.time.LocalDate start, java.time.LocalDate end, int rows) {
        AnalysisRunEntity run = findRun(uploadId);
        findUpload(uploadId).setCoverage(start, end, rows);
        run.start(start, end);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advancePhase(Long uploadId, SalesUploadProcessingPhase phase) {
        findUpload(uploadId).advancePhase(phase);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long uploadId, int appliedRecords) {
        findUpload(uploadId).complete(appliedRecords);
        findRun(uploadId).complete();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long uploadId, String failReason, String errorMessage) {
        findUpload(uploadId).fail(failReason, errorMessage, null);
        findRun(uploadId).fail(errorMessage);
    }

    @Transactional(readOnly = true)
    public Long analysisRunId(Long uploadId) {
        return findRun(uploadId).getId();
    }

    private SalesUploadEntity findUpload(Long uploadId) {
        return uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalStateException("매출 업로드를 찾을 수 없습니다: " + uploadId));
    }

    private AnalysisRunEntity findRun(Long uploadId) {
        return analysisRunRepository.findByBasedOnUploadId(uploadId)
                .orElseThrow(() -> new IllegalStateException("분석 실행을 찾을 수 없습니다: " + uploadId));
    }
}
