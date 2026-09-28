package com.memme.service.sales.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.memme.dto.sales.SalesExpectedForecastResponse;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED;

@Service
@Transactional(readOnly = true)
public class SalesExpectedForecastQueryService {
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final SalesForecastRepository forecastRepository;
    private final StoreOwnershipRepository ownershipRepository;

    public SalesExpectedForecastQueryService(
            SalesDailySummaryRepository dailySummaryRepository,
            SalesForecastRepository forecastRepository,
            StoreOwnershipRepository ownershipRepository
    ) {
        this.dailySummaryRepository = dailySummaryRepository;
        this.forecastRepository = forecastRepository;
        this.ownershipRepository = ownershipRepository;
    }

    public Optional<SalesExpectedForecastResponse> query(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(STORE_OWNER_REQUIRED);
        }

        List<SalesDailySummaryEntity> summaries = dailySummaryRepository
                .findAllByStoreIdOrderBySalesDateAsc(storeId);
        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        LocalDate latestActualDate = summaries.get(summaries.size() - 1).getSalesDate();
        YearMonth targetMonth = YearMonth.from(latestActualDate.plusDays(1));
        LocalDate forecastStart = latestActualDate.plusDays(1);
        LocalDate forecastEnd = targetMonth.atEndOfMonth();
        List<SalesForecastEntity> forecasts = forecastRepository
                .findAllByStoreIdAndTargetDateBetweenOrderByTargetDateAsc(storeId, forecastStart, forecastEnd);
        if (forecasts.isEmpty()) {
            return Optional.empty();
        }

        long actualSalesAmount = summaries.stream()
                .filter(summary -> !summary.getSalesDate().isBefore(targetMonth.atDay(1)))
                .filter(summary -> !summary.getSalesDate().isAfter(latestActualDate))
                .mapToLong(SalesDailySummaryEntity::getTotalNetAmount)
                .sum();
        long forecastSalesAmount = forecasts.stream()
                .mapToLong(SalesForecastEntity::getPredictedSalesAmount)
                .sum();
        long lowerBound = forecasts.stream().mapToLong(SalesForecastEntity::getLowerBound).sum();
        long upperBound = forecasts.stream().mapToLong(SalesForecastEntity::getUpperBound).sum();

        return Optional.of(new SalesExpectedForecastResponse(
                targetMonth,
                BigDecimal.valueOf(actualSalesAmount),
                BigDecimal.valueOf(forecastSalesAmount),
                BigDecimal.valueOf(actualSalesAmount + forecastSalesAmount),
                BigDecimal.valueOf(actualSalesAmount + lowerBound),
                BigDecimal.valueOf(actualSalesAmount + upperBound),
                forecasts.stream().map(forecast -> new SalesExpectedForecastResponse.DailyForecast(
                        forecast.getTargetDate(),
                        BigDecimal.valueOf(forecast.getPredictedSalesAmount()),
                        BigDecimal.valueOf(forecast.getLowerBound()),
                        BigDecimal.valueOf(forecast.getUpperBound())
                )).toList()
        ));
    }
}
