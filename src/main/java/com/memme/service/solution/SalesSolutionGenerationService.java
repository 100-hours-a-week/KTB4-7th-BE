package com.memme.service.solution;

import java.time.LocalDate;

import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.exception.solution.SalesSolutionAiException;
import com.memme.service.noti.SolutionReadyNotificationService;
import org.springframework.stereotype.Service;

@Service
public class SalesSolutionGenerationService {

    private static final int MAX_STALE_RETRIES = 1;

    private final SalesSolutionGenerationContextResolver contextResolver;
    private final SalesSolutionPersistenceService persistenceService;
    private final SalesSolutionAiClient aiClient;
    private final SolutionReadyNotificationService solutionReadyNotificationService;

    public SalesSolutionGenerationService(
            SalesSolutionGenerationContextResolver contextResolver,
            SalesSolutionPersistenceService persistenceService,
            SalesSolutionAiClient aiClient,
            SolutionReadyNotificationService solutionReadyNotificationService
    ) {
        this.contextResolver = contextResolver;
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.solutionReadyNotificationService = solutionReadyNotificationService;
    }

    public SalesSolutionGenerationResult generateAfterUpload(Long storeId, LocalDate targetDate) {
        return generate(storeId, targetDate, "UPLOAD");
    }

    public SalesSolutionGenerationResult generateScheduled(Long storeId, LocalDate targetDate) {
        return generate(storeId, targetDate, "SCHEDULED");
    }

    public void recordForecastFailure(Long storeId, LocalDate targetDate) {
        contextResolver.latestSalesAnalysisId(storeId).ifPresent(analysisId -> {
            SalesSolutionPersistenceService.StartResult start = persistenceService.start(
                    storeId,
                    analysisId,
                    targetDate
            );
            persistenceService.fail(start.bundle().getId());
        });
    }

    private SalesSolutionGenerationResult generate(
            Long storeId,
            LocalDate targetDate,
            String triggerType
    ) {
        Long bundleId = null;
        try {
            SalesSolutionGenerationContextResolver.Resolution resolution =
                    contextResolver.resolve(storeId, targetDate);
            SalesSolutionGenerationResult unavailable = unavailableResult(resolution);
            if (unavailable != null) {
                return unavailable;
            }
            SalesSolutionGenerationContext context = resolution.context();
            SalesSolutionPersistenceService.StartResult start = persistenceService.start(
                    storeId,
                    context.salesAnalysis().getId(),
                    targetDate
            );
            SolutionBundleEntity bundle = start.bundle();
            bundleId = bundle.getId();
            if (!start.shouldGenerate()) {
                return existingResult(bundle);
            }

            for (int attempt = 0; attempt <= MAX_STALE_RETRIES; attempt++) {
                SalesSolutionGenerationResponse response = generateWithRetry(
                        new SalesSolutionGenerationRequest(
                                storeId,
                                "UPLOAD".equals(triggerType)
                                        ? context.salesAnalysis().getId()
                                        : null,
                                targetDate,
                                triggerType,
                                context.metrics()
                        )
                );
                if (!targetDate.equals(response.data().targetDate())) {
                    throw new SalesSolutionAiException(
                            0,
                            "INVALID_AI_RESPONSE",
                            "솔루션 AI 응답의 대상 날짜가 요청과 다릅니다.",
                            false
                    );
                }

                Long latestAnalysisId = contextResolver.latestSalesAnalysisId(storeId).orElse(null);
                if (!context.salesAnalysis().getId().equals(latestAnalysisId)) {
                    if (attempt < MAX_STALE_RETRIES && latestAnalysisId != null) {
                        resolution = contextResolver.resolve(storeId, targetDate);
                        unavailable = unavailableResult(resolution);
                        if (unavailable != null) {
                            persistenceService.fail(bundleId);
                            return unavailable;
                        }
                        context = resolution.context();
                        persistenceService.restart(bundleId, context.salesAnalysis().getId());
                        continue;
                    }
                    throw new SalesSolutionAiException(
                            0,
                            "STALE_SALES_ANALYSIS",
                            "솔루션 생성 중 최신 매출 분석이 변경되었습니다.",
                            true
                    );
                }

                persistenceService.complete(bundleId, latestAnalysisId, response.data());
                solutionReadyNotificationService.notifySolutionReady(bundle);
                return SalesSolutionGenerationResult.of(
                        SalesSolutionGenerationResult.Status.COMPLETED,
                        bundleId
                );
            }
            throw new IllegalStateException("solution generation retry exhausted");
        } catch (RuntimeException exception) {
            if (bundleId != null) {
                persistenceService.fail(bundleId);
            }
            boolean retryable = exception instanceof SalesSolutionAiException aiException
                    && aiException.isRetryable();
            return new SalesSolutionGenerationResult(
                    SalesSolutionGenerationResult.Status.FAILED,
                    bundleId,
                    retryable
            );
        }
    }

    private SalesSolutionGenerationResponse generateWithRetry(
            SalesSolutionGenerationRequest request
    ) {
        try {
            return aiClient.generate(request);
        } catch (SalesSolutionAiException exception) {
            if (!exception.isRetryable()) {
                throw exception;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SalesSolutionAiException(
                        0,
                        "AI_RETRY_INTERRUPTED",
                        "솔루션 AI 재시도가 중단되었습니다.",
                        true,
                        interrupted
                );
            }
            return aiClient.generate(request);
        }
    }

    private SalesSolutionGenerationResult unavailableResult(
            SalesSolutionGenerationContextResolver.Resolution resolution
    ) {
        return switch (resolution.availability()) {
            case EMPTY -> SalesSolutionGenerationResult.of(
                    SalesSolutionGenerationResult.Status.EMPTY,
                    null
            );
            case INSUFFICIENT_HISTORY -> SalesSolutionGenerationResult.of(
                    SalesSolutionGenerationResult.Status.INSUFFICIENT_HISTORY,
                    null
            );
            case FORECAST_PENDING -> SalesSolutionGenerationResult.of(
                    SalesSolutionGenerationResult.Status.FORECAST_PENDING,
                    null
            );
            case READY -> null;
        };
    }

    private SalesSolutionGenerationResult existingResult(SolutionBundleEntity bundle) {
        SalesSolutionGenerationResult.Status status = switch (bundle.getStatus()) {
            case COMPLETED -> SalesSolutionGenerationResult.Status.COMPLETED;
            case PENDING, GENERATING -> SalesSolutionGenerationResult.Status.GENERATING;
            case FAILED -> SalesSolutionGenerationResult.Status.FAILED;
        };
        return SalesSolutionGenerationResult.of(status, bundle.getId());
    }
}
