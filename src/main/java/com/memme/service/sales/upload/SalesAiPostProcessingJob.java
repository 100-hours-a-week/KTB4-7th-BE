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

    private static final int FORECAST_HORIZON_DAYS = 35;

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
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveForecastStartDate = forecastStartDate.isAfter(today)
                ? today
                : forecastStartDate;
        SalesForecastGenerationResult forecastResult = forecastGenerationService.generate(
                storeId,
                uploadId,
                analysisRunId,
                effectiveForecastStartDate
        );
        if (forecastResult.status() == SalesForecastGenerationResult.Status.COMPLETED
                && !today.isBefore(effectiveForecastStartDate)
                && today.isBefore(effectiveForecastStartDate.plusDays(FORECAST_HORIZON_DAYS))) {
            solutionGenerationService.generateAfterUpload(storeId, today);
        }
    }
}
