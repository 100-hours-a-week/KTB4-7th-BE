package com.memme.service.sales.analysis;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.memme.dto.sales.SalesAnalysisResponse;
import com.memme.dto.sales.SalesCategoriesResponse;
import com.memme.dto.sales.SalesForecastsResponse;
import com.memme.dto.sales.SalesPeriod;
import com.memme.dto.sales.InternalSalesCategoriesResponse;
import com.memme.dto.sales.InternalSalesHourlyProfilesResponse;
import com.memme.dto.sales.InternalSalesSummaryResponse;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.exception.sales.InternalSalesDataNotFoundException;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.INVALID_PERIOD;

@Service
@Transactional(readOnly = true)
public class InternalSalesQueryService {

    private final SalesAnalysisQueryService periodResolver;
    private final SalesAnalysisService analysisService;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesForecastRepository forecastRepository;
    private final Clock clock;

    public InternalSalesQueryService(
            SalesAnalysisQueryService periodResolver,
            SalesAnalysisService analysisService,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            SalesForecastRepository forecastRepository,
            Clock clock
    ) {
        this.periodResolver = periodResolver;
        this.analysisService = analysisService;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.forecastRepository = forecastRepository;
        this.clock = clock;
    }

    public InternalSalesSummaryResponse summary(
            Long storeId,
            String periodType,
            String startDate,
            String endDate
    ) {
        SalesAnalysisResponse analysis = completed(storeId, periodType, startDate, endDate).analysis();
        return new InternalSalesSummaryResponse(
                analysis.period(),
                analysis.kpis().totalSales(),
                analysis.kpis().orderCount(),
                analysis.kpis().averageOrderValue(),
                analysis.kpis().changes().totalSalesRate()
        );
    }

    public InternalSalesCategoriesResponse categories(
            Long storeId,
            String periodType,
            String startDate,
            String endDate
    ) {
        SalesCategoriesResponse categories = completed(
                storeId,
                periodType,
                startDate,
                endDate
        ).categories();
        return new InternalSalesCategoriesResponse(
                categories.categories().stream().map(category ->
                        new InternalSalesCategoriesResponse.Category(
                                category.categoryName(),
                                category.netSales(),
                                category.ratio()
                        )).toList(),
                categories.menuRankings().stream().map(ranking ->
                        new InternalSalesCategoriesResponse.MenuRanking(
                                ranking.rank(),
                                ranking.menuName(),
                                ranking.netSales(),
                                ranking.quantity()
                        )).toList()
        );
    }

    public InternalSalesHourlyProfilesResponse hourlyProfiles(
            Long storeId,
            String periodType,
            String dayOfWeek,
            String startDate,
            String endDate
    ) {
        SalesAnalysisResult.Completed result = completed(storeId, periodType, startDate, endDate);
        SalesPeriod period = result.analysis().period();
        DayOfWeek day = parseDayOfWeek(dayOfWeek);
        List<SalesOrderEntity> orders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        period.startDate().atStartOfDay(),
                        period.endDate().plusDays(1).atStartOfDay()
                ).stream()
                .filter(SalesOrderEntity::isValid)
                .filter(order -> day == null || order.getOrderedAt().getDayOfWeek() == day)
                .toList();
        Map<Integer, Long> orderCounts = orders.stream().collect(Collectors.groupingBy(
                        order -> order.getOrderedAt().getHour(),
                        Collectors.counting()
                ));
        Map<Long, Integer> hoursByOrderId = orders.stream().collect(Collectors.toMap(
                SalesOrderEntity::getId,
                order -> order.getOrderedAt().getHour()
        ));
        Map<Integer, Long> menuSalesByHour = new HashMap<>();
        if (!orders.isEmpty()) {
            for (SalesOrderItemEntity item : itemRepository
                    .findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(hoursByOrderId.keySet())) {
                Integer hour = hoursByOrderId.get(item.getSalesOrderId());
                if (hour != null && item.getItemType() == SalesOrderItemType.MENU) {
                    menuSalesByHour.merge(hour, item.getNetAmount(), Math::addExact);
                }
            }
        }
        List<InternalSalesHourlyProfilesResponse.HourlyProfile> profiles = menuSalesByHour.entrySet().stream()
                .map(entry -> new InternalSalesHourlyProfilesResponse.HourlyProfile(
                        entry.getKey(),
                        BigDecimal.valueOf(entry.getValue()),
                        orderCounts.getOrDefault(entry.getKey(), 0L)
                ))
                .sorted(java.util.Comparator.comparingInt(
                        InternalSalesHourlyProfilesResponse.HourlyProfile::hour
                ))
                .toList();
        if (profiles.isEmpty()) {
            throw new InternalSalesDataNotFoundException();
        }
        return new InternalSalesHourlyProfilesResponse(profiles);
    }

    private DayOfWeek parseDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(dayOfWeek);
        } catch (IllegalArgumentException exception) {
            throw new SalesAnalysisRequestException(INVALID_PERIOD);
        }
    }

    public SalesForecastsResponse forecasts(Long storeId, LocalDate targetDate) {
        LocalDate today = LocalDate.now(clock);
        List<SalesForecastEntity> forecasts = targetDate == null
                ? forecastRepository.findAllByStoreIdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(
                        storeId,
                        today
                )
                : forecastRepository.findByStoreIdAndTargetDate(storeId, targetDate)
                        .filter(forecast -> !forecast.getTargetDate().isBefore(today))
                        .stream().toList();
        if (forecasts.isEmpty()) {
            throw new InternalSalesDataNotFoundException();
        }
        OffsetDateTime generatedAt = forecasts.stream()
                .map(SalesForecastEntity::getGeneratedAt)
                .max(java.time.LocalDateTime::compareTo)
                .map(value -> OffsetDateTime.of(
                        value,
                        clock.getZone().getRules().getOffset(value)
                ))
                .orElseThrow();
        return new SalesForecastsResponse(
                forecasts.stream().map(forecast -> new SalesForecastsResponse.Forecast(
                        forecast.getTargetDate(),
                        BigDecimal.valueOf(forecast.getPredictedSalesAmount()),
                        BigDecimal.valueOf(forecast.getLowerBound()),
                        BigDecimal.valueOf(forecast.getUpperBound())
                )).toList(),
                generatedAt
        );
    }

    private SalesAnalysisResult.Completed completed(
            Long storeId,
            String periodType,
            String startDate,
            String endDate
    ) {
        SalesPeriod period = periodResolver.resolvePeriod(periodType, startDate, endDate);
        return switch (analysisService.analyze(
                storeId,
                period.type(),
                period.startDate(),
                period.endDate()
        )) {
            case SalesAnalysisResult.Completed completed -> completed;
            case SalesAnalysisResult.Empty ignored -> throw new InternalSalesDataNotFoundException();
        };
    }
}
