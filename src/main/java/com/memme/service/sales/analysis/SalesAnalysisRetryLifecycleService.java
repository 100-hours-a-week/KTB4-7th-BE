package com.memme.service.sales.analysis;

import java.time.LocalDate;
import java.util.List;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesAnalysisRetryException;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.sales.SalesAnalysisRetryException.Reason.RETRY_IN_PROGRESS;
import static com.memme.exception.sales.SalesAnalysisRetryException.Reason.RETRY_NOT_ALLOWED;
import static com.memme.exception.sales.SalesAnalysisRetryException.Reason.STORE_OWNER_REQUIRED;
import static com.memme.exception.sales.SalesAnalysisRetryException.Reason.UPLOAD_NOT_FOUND;

@Service
public class SalesAnalysisRetryLifecycleService {

    private static final List<AnalysisRunStatus> ACTIVE_STATUSES = List.of(
            AnalysisRunStatus.PENDING,
            AnalysisRunStatus.PROCESSING
    );

    private final SalesUploadRepository uploadRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final StoreOwnershipRepository ownershipRepository;

    public SalesAnalysisRetryLifecycleService(
            SalesUploadRepository uploadRepository,
            AnalysisRunRepository analysisRunRepository,
            StoreOwnershipRepository ownershipRepository
    ) {
        this.uploadRepository = uploadRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.ownershipRepository = ownershipRepository;
    }

    @Transactional
    public Reservation reserve(Long userId, Long storeId, Long uploadId) {
        if (userId == null || storeId == null
                || !ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRetryException(STORE_OWNER_REQUIRED, "STORE_OWNER_REQUIRED");
        }
        SalesUploadEntity upload = uploadRepository.findByIdForUpdate(uploadId)
                .filter(found -> found.getStoreId().equals(storeId))
                .filter(found -> found.getRequestedByUserId().equals(userId))
                .orElseThrow(() -> new SalesAnalysisRetryException(UPLOAD_NOT_FOUND, "UPLOAD_NOT_FOUND"));

        if (analysisRunRepository.existsByBasedOnUploadIdAndStatusIn(uploadId, ACTIVE_STATUSES)) {
            throw new SalesAnalysisRetryException(RETRY_IN_PROGRESS, "RETRY_IN_PROGRESS");
        }
        AnalysisRunEntity latestRun = analysisRunRepository
                .findFirstByBasedOnUploadIdOrderByIdDesc(uploadId)
                .orElseThrow(() -> new SalesAnalysisRetryException(UPLOAD_NOT_FOUND, "UPLOAD_NOT_FOUND"));
        if (upload.getStatus() != SalesUploadStatus.FAILED
                || latestRun.getStatus() != AnalysisRunStatus.FAILED
                || !isRetryableFailure(upload.getErrorCode())) {
            throw new SalesAnalysisRetryException(RETRY_NOT_ALLOWED, "INVALID_SALES_SCHEMA");
        }

        AnalysisRunEntity retryRun = analysisRunRepository.saveAndFlush(
                AnalysisRunEntity.pending(storeId, userId, uploadId)
        );
        return new Reservation(
                upload.getId(),
                upload.getStoreId(),
                upload.getStorageKey(),
                retryRun.getId()
        );
    }

    private boolean isRetryableFailure(String errorCode) {
        return "UPLOAD_PROCESSING_ERROR".equals(errorCode)
                || "ANALYSIS_ENGINE_ERROR".equals(errorCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(Long analysisRunId, LocalDate periodStart, LocalDate periodEnd) {
        findRun(analysisRunId).start(periodStart, periodEnd);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long analysisRunId) {
        findRun(analysisRunId).complete();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long analysisRunId, String message) {
        findRun(analysisRunId).fail(message);
    }

    private AnalysisRunEntity findRun(Long analysisRunId) {
        return analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new IllegalStateException("분석 실행을 찾을 수 없습니다: " + analysisRunId));
    }

    public record Reservation(
            Long uploadId,
            Long storeId,
            String storageKey,
            Long analysisRunId
    ) {}
}
