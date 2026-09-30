package com.memme.service.sales.upload;

import java.time.Clock;
import java.time.LocalDate;

import com.memme.service.sales.forecast.SalesForecastGenerationResult;
import com.memme.service.sales.forecast.SalesForecastGenerationService;
import com.memme.service.solution.SalesSolutionGenerationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SalesAiPostProcessingJob {

    private final SalesForecastGenerationService forecastGenerationService;
    private final SalesSolutionGenerationService solutionGenerationService;
    private final Clock clock;

    public SalesAiPostProcessingJob(
            SalesForecastGenerationService forecastGenerationService,
            SalesSolutionGenerationService solutionGenerationService,
            Clock clock
    ) {
        this.forecastGenerationService = forecastGenerationService;
        this.solutionGenerationService = solutionGenerationService;
        this.clock = clock;
    }

    @Async
    public void start(
            Long storeId,
            Long uploadId,
            Long analysisRunId,
            LocalDate forecastStartDate
    ) {
        SalesForecastGenerationResult forecastResult = forecastGenerationService.generate(
                storeId,
                uploadId,
                analysisRunId,
                forecastStartDate
        );
        if (forecastResult.status() == SalesForecastGenerationResult.Status.COMPLETED
                && forecastStartDate.equals(LocalDate.now(clock))) {
            solutionGenerationService.generateAfterUpload(storeId, forecastStartDate);
        }
    }
}
