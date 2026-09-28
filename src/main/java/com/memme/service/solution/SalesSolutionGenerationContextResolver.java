package com.memme.service.solution;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesForecastRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SalesSolutionGenerationContextResolver {

    private final AnalysisRunRepository analysisRunRepository;
    private final SalesAnalysisRepository salesAnalysisRepository;
    private final SalesForecastRepository forecastRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final SalesSolutionMetricsAssembler metricsAssembler;

    public SalesSolutionGenerationContextResolver(
            AnalysisRunRepository analysisRunRepository,
            SalesAnalysisRepository salesAnalysisRepository,
            SalesForecastRepository forecastRepository,
            SalesDailySummaryRepository dailySummaryRepository,
            SalesSolutionMetricsAssembler metricsAssembler
    ) {
        this.analysisRunRepository = analysisRunRepository;
        this.salesAnalysisRepository = salesAnalysisRepository;
        this.forecastRepository = forecastRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.metricsAssembler = metricsAssembler;
    }

    public Resolution resolve(Long storeId, LocalDate targetDate) {
        Optional<AnalysisRunEntity> run = latestCompletedRun(storeId);
        if (run.isEmpty()) {
            return new Resolution(Availability.EMPTY, null);
        }
        Optional<SalesAnalysisEntity> analysis = salesAnalysisRepository
                .findByAnalysisRunId(run.orElseThrow().getId());
        if (analysis.isEmpty()) {
            return new Resolution(Availability.EMPTY, null);
        }
        if (historyCoverage(storeId, targetDate) == HistoryCoverage.INSUFFICIENT) {
            return new Resolution(Availability.INSUFFICIENT_HISTORY, null);
        }
        return forecastRepository.findByStoreIdAndTargetDate(storeId, targetDate)
                .<Resolution>map(forecast -> new Resolution(
                        Availability.READY,
                        new SalesSolutionGenerationContext(
                                run.orElseThrow(),
                                analysis.orElseThrow(),
                                metricsAssembler.assemble(storeId, run.orElseThrow(), forecast)
                        )
                ))
                .orElseGet(() -> new Resolution(
                        Availability.FORECAST_PENDING,
                        null
                ));
    }

    public Optional<Long> latestSalesAnalysisId(Long storeId) {
        return latestCompletedRun(storeId)
                .flatMap(run -> salesAnalysisRepository.findByAnalysisRunId(run.getId()))
                .map(SalesAnalysisEntity::getId);
    }

    private Optional<AnalysisRunEntity> latestCompletedRun(Long storeId) {
        return analysisRunRepository.findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
                storeId,
                AnalysisRunStatus.COMPLETED
        );
    }

    public HistoryCoverage historyCoverage(Long storeId, LocalDate targetDate) {
        List<LocalDate> dates = dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(storeId)
                .stream()
                .map(summary -> summary.getSalesDate())
                .filter(date -> date.isBefore(targetDate))
                .distinct()
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            return HistoryCoverage.INSUFFICIENT;
        }
        Set<LocalDate> availableDates = new HashSet<>(dates);
        LocalDate latestDate = dates.getLast();
        if (!latestDate.equals(targetDate.minusDays(1))) {
            return HistoryCoverage.INSUFFICIENT;
        }
        LocalDate continuousStart = latestDate;
        while (availableDates.contains(continuousStart.minusDays(1))) {
            continuousStart = continuousStart.minusDays(1);
        }
        if (coversAtLeast(continuousStart, latestDate, continuousStart.plusYears(1))) {
            return HistoryCoverage.SUFFICIENT;
        }
        if (coversAtLeast(continuousStart, latestDate, continuousStart.plusMonths(3))) {
            return HistoryCoverage.LIMITED;
        }
        return HistoryCoverage.INSUFFICIENT;
    }

    private boolean coversAtLeast(LocalDate startDate, LocalDate endDate, LocalDate requiredExclusiveEnd) {
        return !requiredExclusiveEnd.isAfter(endDate.plusDays(1));
    }

    public enum Availability {
        READY,
        FORECAST_PENDING,
        INSUFFICIENT_HISTORY,
        EMPTY
    }

    public enum HistoryCoverage {
        INSUFFICIENT,
        LIMITED,
        SUFFICIENT
    }

    public record Resolution(
            Availability availability,
            SalesSolutionGenerationContext context
    ) {}
}
