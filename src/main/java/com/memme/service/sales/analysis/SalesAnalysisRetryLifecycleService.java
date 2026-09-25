package com.memme.service.sales.analysis;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
    private final SalesAiInsightRepository insightRepository;
    private final StoreOwnershipRepository ownershipRepository;

    public SalesAnalysisRetryLifecycleService(
            SalesUploadRepository uploadRepository,
            AnalysisRunRepository analysisRunRepository,
            SalesAiInsightRepository insightRepository,
            StoreOwnershipRepository ownershipRepository
    ) {
        this.uploadRepository = uploadRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.insightRepository = insightRepository;
        this.ownershipRepository = ownershipRepository;
    }

    @Transactional
    public Reservation reserve(Long userId, Long storeId, Long uploadId) {
        validateOwner(userId, storeId);
        SalesUploadEntity upload = uploadRepository.findByIdForUpdate(uploadId)
                .filter(found -> found.getStoreId().equals(storeId))
                .filter(found -> found.getRequestedByUserId().equals(userId))
                .orElseThrow(() -> new SalesAnalysisRetryException(UPLOAD_NOT_FOUND, "UPLOAD_NOT_FOUND"));

        if (analysisRunRepository.existsByBasedOnUploadIdAndStatusIn(uploadId, ACTIVE_STATUSES)) {
            throw new SalesAnalysisRetryException(RETRY_IN_PROGRESS, "RETRY_IN_PROGRESS");
        }
        if (upload.getStatus() != SalesUploadStatus.COMPLETED
                || upload.getPeriodStart() == null
                || upload.getPeriodEnd() == null) {
            throw retryNotAllowed();
        }

        YearMonth targetMonth = YearMonth.from(upload.getPeriodEnd());
        SalesAiInsightEntity insight = insightRepository
                .findByStoreIdAndTargetMonth(storeId, targetMonth)
                .orElseThrow(this::retryNotAllowed);
        if (insight.getStatus() == SalesAiInsightStatus.PENDING
                || insight.getStatus() == SalesAiInsightStatus.GENERATING) {
            throw new SalesAnalysisRetryException(RETRY_IN_PROGRESS, "RETRY_IN_PROGRESS");
        }
        if (insight.getStatus() != SalesAiInsightStatus.FAILED) {
            throw retryNotAllowed();
        }

        AnalysisRunEntity retryRun = analysisRunRepository.saveAndFlush(
                AnalysisRunEntity.pending(storeId, userId, uploadId)
        );
        return new Reservation(
                upload.getId(),
                storeId,
                insight.getSalesAnalysisId(),
                retryRun.getId(),
                upload.getPeriodStart(),
                upload.getPeriodEnd(),
                targetMonth
        );
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

    private void validateOwner(Long userId, Long storeId) {
        if (userId == null || storeId == null
                || !ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRetryException(STORE_OWNER_REQUIRED, "STORE_OWNER_REQUIRED");
        }
    }

    private AnalysisRunEntity findRun(Long analysisRunId) {
        return analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new IllegalStateException("분석 실행을 찾을 수 없습니다: " + analysisRunId));
    }

    private SalesAnalysisRetryException retryNotAllowed() {
        return new SalesAnalysisRetryException(RETRY_NOT_ALLOWED, "INVALID_SALES_SCHEMA");
    }

    public record Reservation(
            Long uploadId,
            Long storeId,
            Long salesAnalysisId,
            Long analysisRunId,
            LocalDate periodStart,
            LocalDate periodEnd,
            YearMonth targetMonth
    ) {}
}
