package com.memme.service.solution;

import java.time.LocalDate;
import java.time.YearMonth;
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
                        hasRequiredHistory(storeId, targetDate)
                                ? Availability.FORECAST_PENDING
                                : Availability.INSUFFICIENT_HISTORY,
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

    private boolean hasRequiredHistory(Long storeId, LocalDate targetDate) {
        List<LocalDate> dates = dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(storeId)
                .stream()
                .map(summary -> summary.getSalesDate())
                .filter(date -> date.isBefore(targetDate))
                .toList();
        if (dates.size() < 60) {
            return false;
        }
        YearMonth previousMonth = YearMonth.from(targetDate).minusMonths(1);
        YearMonth firstRequiredMonth = previousMonth.minusMonths(1);
        Set<LocalDate> availableDates = new HashSet<>(dates);
        return firstRequiredMonth.atDay(1)
                .datesUntil(previousMonth.atEndOfMonth().plusDays(1))
                .allMatch(availableDates::contains);
    }

    public enum Availability {
        READY,
        FORECAST_PENDING,
        INSUFFICIENT_HISTORY,
        EMPTY
    }

    public record Resolution(
            Availability availability,
            SalesSolutionGenerationContext context
    ) {}
}
