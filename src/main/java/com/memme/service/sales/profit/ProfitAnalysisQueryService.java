package com.memme.service.sales.profit;

import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED;

import com.memme.dto.sales.ProfitAnalysisResponse;
import com.memme.dto.sales.ProfitMissingCostsResponse;
import com.memme.dto.sales.SalesPeriod;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.store.StoreCostItem;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreCostItemRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.analysis.SalesAnalysisQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfitAnalysisQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProfitAnalysisQueryService.class);

    private final SalesAnalysisQueryService salesAnalysisQueryService;
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final StoreCostItemRepository costItemRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final ProfitCalculationService calculationService;
    private final ProfitInsightService insightService;

    public ProfitAnalysisQueryService(SalesAnalysisQueryService salesAnalysisQueryService,
            SalesDailySummaryRepository dailySummaryRepository, StoreCostItemRepository costItemRepository,
            StoreOwnershipRepository ownershipRepository, ProfitCalculationService calculationService,
            ProfitInsightService insightService) {
        this.salesAnalysisQueryService = salesAnalysisQueryService;
        this.dailySummaryRepository = dailySummaryRepository;
        this.costItemRepository = costItemRepository;
        this.ownershipRepository = ownershipRepository;
        this.calculationService = calculationService;
        this.insightService = insightService;
    }

    public ProfitAnalysisQueryResult query(Long userId, Long storeId, String periodType,
            String startDate, String endDate) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(STORE_OWNER_REQUIRED);
        }
        SalesPeriod period = salesAnalysisQueryService.resolvePeriod(periodType, startDate, endDate);
        ProfitCalculationResult current = calculate(storeId, period.startDate(), period.endDate());
        if (current.status() == ProfitCalculationResult.Status.COST_INPUT_REQUIRED) {
            return new ProfitAnalysisQueryResult.CostInputRequired(new ProfitMissingCostsResponse(
                    current.missingCostMonths().stream().map(YearMonth::toString).toList()));
        }
        if (current.status() == ProfitCalculationResult.Status.EMPTY) {
            return new ProfitAnalysisQueryResult.Empty();
        }

        DateRange previousRange = previousRange(period);
        ProfitCalculationResult previous = calculate(storeId, previousRange.start(), previousRange.end());
        Long previousNetProfit = previous.status() == ProfitCalculationResult.Status.COMPLETED
                ? previous.amounts().netProfit() : null;
        ProfitCalculationResult.Amounts amounts = current.amounts();
        BigDecimal netProfitRate = ratio(amounts.netProfit(), amounts.totalNetAmount());
        BigDecimal changeRate = previousNetProfit == null || previousNetProfit == 0
                ? null : BigDecimal.valueOf(amounts.netProfit())
                        .subtract(BigDecimal.valueOf(previousNetProfit))
                        .divide(BigDecimal.valueOf(previousNetProfit).abs(), 4, RoundingMode.HALF_UP);

        List<ProfitAnalysisResponse.DailyProfit> dailyProfits = current.dailyProfits().stream()
                .map(day -> new ProfitAnalysisResponse.DailyProfit(day.date(), day.netProfit()))
                .toList();
        ProfitAnalysisResponse response = new ProfitAnalysisResponse(
                new ProfitAnalysisResponse.Summary(amounts.totalNetAmount(), amounts.ingredientCost(),
                        amounts.fixedCost(), amounts.netProfit(), netProfitRate, previousNetProfit, changeRate),
                dailyProfits,
                describeSafely(storeId, amounts.netProfit(), previousNetProfit, dailyProfits)
        );
        return new ProfitAnalysisQueryResult.Completed(response);
    }

    private ProfitCalculationResult calculate(Long storeId, LocalDate start, LocalDate end) {
        List<DailyProfitSales> sales = dailySummaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(storeId, start, end)
                .stream()
                .map(this::toSales)
                .toList();
        List<MonthlyProfitCost> costs = costItemRepository
                .findAllByStoreIdAndCostMonthBetweenOrderByCostMonthAsc(
                        storeId, YearMonth.from(start).atDay(1), YearMonth.from(end).atDay(1))
                .stream()
                .map(this::toCost)
                .toList();
        return calculationService.calculate(start, end, sales, costs);
    }

    private ProfitAnalysisResponse.AiInsight describeSafely(Long storeId, long netProfit,
            Long previousNetProfit, List<ProfitAnalysisResponse.DailyProfit> dailyProfits) {
        try {
            return insightService.describe(netProfit, previousNetProfit, dailyProfits);
        } catch (RuntimeException exception) {
            log.warn("Profit insight creation failed for storeId={}", storeId, exception);
            return new ProfitAnalysisResponse.AiInsight("FAILED", List.of());
        }
    }

    private DailyProfitSales toSales(SalesDailySummaryEntity summary) {
        return new DailyProfitSales(summary.getSalesDate(), summary.getTotalNetAmount());
    }

    private MonthlyProfitCost toCost(StoreCostItem cost) {
        return new MonthlyProfitCost(YearMonth.from(cost.getCostMonth()), cost.getRentAmount(),
                cost.getLaborAmount(), cost.getIngredientCostRate());
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private DateRange previousRange(SalesPeriod period) {
        LocalDate start = period.startDate();
        LocalDate end = period.endDate();
        return switch (period.type()) {
            case "TODAY" -> new DateRange(start.minusDays(1), end.minusDays(1));
            case "THIS_WEEK" -> new DateRange(start.minusWeeks(1), end.minusWeeks(1));
            case "THIS_MONTH" -> new DateRange(start.minusMonths(1), end.minusMonths(1));
            default -> {
                YearMonth month = YearMonth.from(start);
                if (start.equals(month.atDay(1)) && end.equals(month.atEndOfMonth())) {
                    YearMonth previousMonth = month.minusMonths(1);
                    yield new DateRange(previousMonth.atDay(1), previousMonth.atEndOfMonth());
                }
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                LocalDate comparisonEnd = start.minusDays(1);
                yield new DateRange(comparisonEnd.minusDays(days - 1), comparisonEnd);
            }
        };
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
