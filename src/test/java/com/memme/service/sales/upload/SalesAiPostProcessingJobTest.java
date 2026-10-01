package com.memme.service.sales.upload;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.memme.service.sales.forecast.SalesForecastGenerationResult;
import com.memme.service.sales.forecast.SalesForecastGenerationService;
import com.memme.service.solution.SalesSolutionGenerationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesAiPostProcessingJobTest {

    @Test
    void generatesTodaySolutionWhenTodayIsWithinForecastHorizon() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesSolutionGenerationService solutionGenerationService = mock(SalesSolutionGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(
                forecastGenerationService,
                solutionGenerationService,
                clock
        );
        LocalDate forecastStartDate = LocalDate.of(2026, 10, 1);
        when(forecastGenerationService.generate(10L, 20L, 30L, forecastStartDate))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.COMPLETED,
                        false
                ));

        job.start(10L, 20L, 30L, forecastStartDate);

        verify(solutionGenerationService).generateAfterUpload(10L, LocalDate.of(2026, 10, 2));
    }

    @Test
    void generatesTodaySolutionAfterFirstUploadEndingYesterday() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesSolutionGenerationService solutionGenerationService = mock(SalesSolutionGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(
                forecastGenerationService,
                solutionGenerationService,
                clock
        );
        LocalDate today = LocalDate.of(2026, 9, 30);
        when(forecastGenerationService.generate(10L, 20L, 30L, today))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.COMPLETED,
                        false
                ));

        job.start(10L, 20L, 30L, today);

        verify(solutionGenerationService).generateAfterUpload(10L, today);
    }

    @Test
    void excludesTodaySalesFromForecastWhenUploadContainsTodaySales() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesSolutionGenerationService solutionGenerationService = mock(SalesSolutionGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(
                forecastGenerationService,
                solutionGenerationService,
                clock
        );
        LocalDate tomorrow = LocalDate.of(2026, 10, 1);
        LocalDate today = LocalDate.of(2026, 9, 30);
        when(forecastGenerationService.generate(10L, 20L, 30L, today))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.COMPLETED,
                        false
                ));

        job.start(10L, 20L, 30L, tomorrow);

        verify(forecastGenerationService).generate(10L, 20L, 30L, today);
        verify(solutionGenerationService).generateAfterUpload(10L, today);
    }

    @Test
    void generatesForecastAfterUpload() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesSolutionGenerationService solutionGenerationService = mock(SalesSolutionGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(
                forecastGenerationService,
                solutionGenerationService,
                clock
        );
        LocalDate forecastStartDate = LocalDate.of(2026, 8, 26);
        when(forecastGenerationService.generate(10L, 20L, 30L, forecastStartDate))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.COMPLETED,
                        false
                ));

        job.start(10L, 20L, 30L, forecastStartDate);

        verify(forecastGenerationService).generate(10L, 20L, 30L, forecastStartDate);
        verify(solutionGenerationService, never()).generateAfterUpload(10L, forecastStartDate);
    }

    @Test
    void requestsForecastEvenWhenItFails() {
        SalesForecastGenerationService forecastGenerationService = mock(SalesForecastGenerationService.class);
        SalesSolutionGenerationService solutionGenerationService = mock(SalesSolutionGenerationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
        SalesAiPostProcessingJob job = new SalesAiPostProcessingJob(
                forecastGenerationService,
                solutionGenerationService,
                clock
        );
        LocalDate forecastStartDate = LocalDate.of(2026, 9, 30);
        when(forecastGenerationService.generate(10L, 20L, 30L, forecastStartDate))
                .thenReturn(new SalesForecastGenerationResult(
                        SalesForecastGenerationResult.Status.FAILED,
                        true
                ));

        job.start(10L, 20L, 30L, forecastStartDate);

        verify(forecastGenerationService).generate(10L, 20L, 30L, forecastStartDate);
        verify(solutionGenerationService, never()).generateAfterUpload(10L, forecastStartDate);
    }
}
