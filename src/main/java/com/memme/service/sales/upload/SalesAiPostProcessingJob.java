package com.memme.service.sales.upload;

import java.time.LocalDate;

import com.memme.service.sales.forecast.SalesForecastGenerationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SalesAiPostProcessingJob {

    private final SalesForecastGenerationService forecastGenerationService;

    public SalesAiPostProcessingJob(SalesForecastGenerationService forecastGenerationService) {
        this.forecastGenerationService = forecastGenerationService;
    }

    @Async
    public void start(
            Long storeId,
            Long uploadId,
            Long analysisRunId,
            LocalDate forecastStartDate
    ) {
        forecastGenerationService.generate(
                storeId,
                uploadId,
                analysisRunId,
                forecastStartDate
        );
    }
}
