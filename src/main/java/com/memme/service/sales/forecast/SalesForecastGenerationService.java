package com.memme.service.sales.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.dto.sales.SalesForecastBatchRequest;
import com.memme.dto.sales.SalesForecastBatchResponse;
import com.memme.dto.sales.SalesForecastStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.exception.sales.SalesForecastAiException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import org.springframework.stereotype.Service;

@Service
public class SalesForecastGenerationService {

    private final SalesDailySummaryRepository dailySummaryRepository;
    private final SalesForecastAiClient aiClient;
    private final SalesForecastService forecastService;

    public SalesForecastGenerationService(
            SalesDailySummaryRepository dailySummaryRepository,
            SalesForecastAiClient aiClient,
            SalesForecastService forecastService
    ) {
        this.dailySummaryRepository = dailySummaryRepository;
        this.aiClient = aiClient;
        this.forecastService = forecastService;
    }

    public SalesForecastGenerationResult generate(
            Long storeId,
            Long uploadId,
            Long analysisRunId,
            LocalDate forecastStartDate
    ) {
        List<SalesForecastBatchRequest.DailySales> dailySales = dailySummaryRepository
                .findAllByStoreIdOrderBySalesDateAsc(storeId).stream()
                .filter(summary -> summary.getSalesDate().isBefore(forecastStartDate))
                .map(this::toDailySales)
                .toList();
        try {
            SalesForecastBatchResponse response = createForecastWithRetry(
                    new SalesForecastBatchRequest(
                            storeId,
                            uploadId,
                            analysisRunId,
                            forecastStartDate,
                            dailySales
                    )
            );
            if (response.status() == SalesForecastStatus.INSUFFICIENT_DATA) {
                return new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.INSUFFICIENT_HISTORY,
                        false
                );
            }
            LocalDateTime generatedAt = LocalDateTime.now();
            for (SalesForecastBatchResponse.Prediction prediction : response.data().predictions()) {
                forecastService.upsert(new SalesForecastUpsertCommand(
                        storeId,
                        uploadId,
                        prediction.targetDate(),
                        forecastStartDate.minusDays(1),
                        prediction.predictedSalesAmount(),
                        prediction.lowerBound(),
                        prediction.upperBound(),
                        prediction.modelVersion(),
                        generatedAt
                ));
            }
            return new SalesForecastGenerationResult(
                    SalesForecastGenerationResult.Status.COMPLETED,
                    false
            );
        } catch (SalesForecastAiException exception) {
            return new SalesForecastGenerationResult(
                    SalesForecastGenerationResult.Status.FAILED,
                    exception.isRetryable()
            );
        }
    }

    private SalesForecastBatchResponse createForecastWithRetry(SalesForecastBatchRequest request) {
        try {
            return aiClient.createForecast(request);
        } catch (SalesForecastAiException exception) {
            if (!exception.isRetryable()) {
                throw exception;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SalesForecastAiException(
                        0,
                        "AI_RETRY_INTERRUPTED",
                        "매출 예측 AI 재시도가 중단되었습니다.",
                        true,
                        interrupted
                );
            }
            return aiClient.createForecast(request);
        }
    }

    private SalesForecastBatchRequest.DailySales toDailySales(SalesDailySummaryEntity summary) {
        return new SalesForecastBatchRequest.DailySales(
                summary.getSalesDate(),
                summary.getMenuNetAmount(),
                (long) summary.getOrderCount()
        );
    }
}
