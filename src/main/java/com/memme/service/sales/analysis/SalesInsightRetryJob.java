package com.memme.service.sales.analysis;

import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.service.sales.insight.SalesInsightGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SalesInsightRetryJob {

    private static final Logger log = LoggerFactory.getLogger(SalesInsightRetryJob.class);

    private final SalesAnalysisRetryLifecycleService lifecycleService;
    private final SalesInsightGenerationService generationService;

    public SalesInsightRetryJob(
            SalesAnalysisRetryLifecycleService lifecycleService,
            SalesInsightGenerationService generationService
    ) {
        this.lifecycleService = lifecycleService;
        this.generationService = generationService;
    }

    @Async
    public void start(SalesAnalysisRetryLifecycleService.Reservation reservation) {
        try {
            lifecycleService.start(
                    reservation.analysisRunId(),
                    reservation.periodStart(),
                    reservation.periodEnd()
            );
            SalesAiInsightStatus status = generationService.generate(
                    reservation.storeId(),
                    reservation.salesAnalysisId(),
                    reservation.analysisRunId(),
                    reservation.targetMonth(),
                    SalesInsightTriggerType.RETRY
            );
            if (status == SalesAiInsightStatus.FAILED) {
                lifecycleService.fail(
                        reservation.analysisRunId(),
                        "AI 인사이트 재시도에 실패했습니다."
                );
                return;
            }
            lifecycleService.complete(reservation.analysisRunId());
        } catch (RuntimeException exception) {
            recordFailure(reservation.analysisRunId(), exception);
        }
    }

    private void recordFailure(Long analysisRunId, RuntimeException exception) {
        try {
            lifecycleService.fail(analysisRunId, "AI 인사이트 재시도에 실패했습니다.");
        } catch (RuntimeException statusException) {
            exception.addSuppressed(statusException);
        }
        log.warn("Sales insight retry failed for analysisRunId={}", analysisRunId, exception);
    }
}
