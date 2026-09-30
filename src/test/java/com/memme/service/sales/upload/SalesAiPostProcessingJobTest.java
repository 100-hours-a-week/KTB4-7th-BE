package com.memme.service.sales.upload;

import java.time.LocalDate;

import com.memme.service.sales.forecast.SalesForecastGenerationResult;
import com.memme.service.sales.forecast.SalesForecastGenerationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesAiPostProcessingJobTest {

    @Test
    void generatesForecastAfterUpload() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(forecastGenerationService);
        LocalDate forecastStartDate = LocalDate.of(2026, 10, 1);
        when(forecastGenerationService.generate(10L, 20L, 30L, forecastStartDate))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.COMPLETED,
                        false
                ));

        job.start(10L, 20L, 30L, forecastStartDate);

        verify(forecastGenerationService).generate(10L, 20L, 30L, forecastStartDate);
    }

    @Test
    void requestsForecastEvenWhenItFails() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(forecastGenerationService);
        LocalDate forecastStartDate = LocalDate.of(2026, 10, 1);
        when(forecastGenerationService.generate(10L, 20L, 30L, forecastStartDate))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.FAILED,
                        true
                ));

        job.start(10L, 20L, 30L, forecastStartDate);

        verify(forecastGenerationService).generate(10L, 20L, 30L, forecastStartDate);
    }
}
