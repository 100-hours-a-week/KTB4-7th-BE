package com.memme.service.sales.insight;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;

import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightResponse;
import com.memme.dto.sales.SalesInsightResponseData;
import com.memme.dto.sales.SalesInsightStatus;
import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.entity.sales.SalesAiInsightStatus;
import org.springframework.stereotype.Service;

@Service
public class SalesInsightGenerationService {

    private static final int MAX_INSIGHT_COUNT = 3;
    private static final int MAX_INSIGHT_LENGTH = 100;

    private final SalesInsightMetricsAssembler metricsAssembler;
    private final SalesInsightClient insightClient;
    private final SalesAiInsightPersistenceService persistenceService;

    public SalesInsightGenerationService(
            SalesInsightMetricsAssembler metricsAssembler,
            SalesInsightClient insightClient,
            SalesAiInsightPersistenceService persistenceService
    ) {
        this.metricsAssembler = metricsAssembler;
        this.insightClient = insightClient;
        this.persistenceService = persistenceService;
    }

    public SalesAiInsightStatus generate(
            Long storeId,
            Long salesAnalysisId,
            Long analysisRunId,
            YearMonth targetMonth,
            SalesInsightTriggerType triggerType
    ) {
        SalesInsightMetricsAssembler.Result assembled = metricsAssembler.assemble(storeId, targetMonth);
        Long insightId = persistenceService.start(storeId, salesAnalysisId, targetMonth);
        if (!assembled.sufficientData()) {
            persistenceService.markInsufficientData(insightId);
            return SalesAiInsightStatus.INSUFFICIENT_DATA;
        }

        SalesInsightRequest request = new SalesInsightRequest(
                storeId,
                salesAnalysisId,
                analysisRunId,
                targetMonth,
                triggerType,
                assembled.metrics(),
                MAX_INSIGHT_COUNT
        );
        try {
            SalesInsightResponse response = insightClient.generate(request);
            return persistResponse(insightId, targetMonth, response);
        } catch (SalesInsightClientException | InvalidSalesInsightResponseException exception) {
            persistenceService.fail(insightId);
            return SalesAiInsightStatus.FAILED;
        }
    }

    private SalesAiInsightStatus persistResponse(
            Long insightId,
            YearMonth targetMonth,
            SalesInsightResponse response
    ) {
        if (response == null || response.status() == null) {
            throw new InvalidSalesInsightResponseException("AI 인사이트 응답 상태가 없습니다.");
        }
        return switch (response.status()) {
            case COMPLETED -> {
                List<String> insights = validateCompleted(targetMonth, response.data());
                persistenceService.complete(insightId, insights);
                yield SalesAiInsightStatus.COMPLETED;
            }
            case INSUFFICIENT_DATA -> {
                persistenceService.markInsufficientData(insightId);
                yield SalesAiInsightStatus.INSUFFICIENT_DATA;
            }
            case FAILED -> {
                persistenceService.fail(insightId);
                yield SalesAiInsightStatus.FAILED;
            }
        };
    }

    private List<String> validateCompleted(
            YearMonth expectedTargetMonth,
            SalesInsightResponseData data
    ) {
        if (data == null || !expectedTargetMonth.equals(data.targetMonth())) {
            throw new InvalidSalesInsightResponseException("AI 인사이트 대상 월이 일치하지 않습니다.");
        }
        List<String> insights = data.insights();
        if (insights == null || insights.isEmpty() || insights.size() > MAX_INSIGHT_COUNT) {
            throw new InvalidSalesInsightResponseException("AI 인사이트 개수가 올바르지 않습니다.");
        }
        HashSet<String> unique = new HashSet<>();
        for (String insight : insights) {
            if (insight == null || insight.isBlank() || insight.length() > MAX_INSIGHT_LENGTH) {
                throw new InvalidSalesInsightResponseException("AI 인사이트 문장이 올바르지 않습니다.");
            }
            if (!unique.add(insight)) {
                throw new InvalidSalesInsightResponseException("AI 인사이트 문장이 중복되었습니다.");
            }
        }
        return List.copyOf(insights);
    }

    private static final class InvalidSalesInsightResponseException extends RuntimeException {
        private InvalidSalesInsightResponseException(String message) {
            super(message);
        }
    }
}
