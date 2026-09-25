package com.memme.service.sales.analysis;

import com.memme.dto.sales.SalesAnalysisRetryResponse;
import com.memme.exception.sales.SalesAnalysisRetryException;
import org.springframework.stereotype.Service;

import static com.memme.exception.sales.SalesAnalysisRetryException.Reason.RETRY_FAILED;

@Service
public class SalesAnalysisRetryService {

    private final SalesAnalysisRetryLifecycleService lifecycleService;
    private final SalesInsightRetryJob retryJob;

    public SalesAnalysisRetryService(
            SalesAnalysisRetryLifecycleService lifecycleService,
            SalesInsightRetryJob retryJob
    ) {
        this.lifecycleService = lifecycleService;
        this.retryJob = retryJob;
    }

    public SalesAnalysisRetryResponse retry(Long userId, Long storeId, Long uploadId) {
        SalesAnalysisRetryLifecycleService.Reservation reservation =
                lifecycleService.reserve(userId, storeId, uploadId);
        try {
            retryJob.start(reservation);
        } catch (RuntimeException exception) {
            lifecycleService.fail(reservation.analysisRunId(), "AI 인사이트 재시도 접수에 실패했습니다.");
            throw new SalesAnalysisRetryException(
                    RETRY_FAILED,
                    "INSIGHT_RETRY_FAILED",
                    exception
            );
        }
        return new SalesAnalysisRetryResponse(
                reservation.uploadId(),
                reservation.analysisRunId()
        );
    }
}
