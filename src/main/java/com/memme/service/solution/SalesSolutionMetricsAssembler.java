package com.memme.service.solution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SalesSolutionMetricsAssembler {

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;

    public SalesSolutionMetricsAssembler(
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
    }

    public SalesSolutionMetrics assemble(
            Long storeId,
            AnalysisRunEntity analysisRun,
            SalesForecastEntity forecast
    ) {
        LocalDate start = analysisRun.getPeriodStart();
        LocalDate end = analysisRun.getPeriodEnd();
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);

        PeriodItems current = load(storeId, start, end);
        PeriodItems previous = load(storeId, previousStart, previousEnd);
        long currentNetSales = current.menuItems().stream()
                .mapToLong(SalesOrderItemEntity::getNetAmount)
                .sum();
        long previousNetSales = previous.menuItems().stream()
                .mapToLong(SalesOrderItemEntity::getNetAmount)
                .sum();

        return new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(
                        currentNetSales,
                        changeRate(currentNetSales, previousNetSales)
                ),
                hourlyProfile(current),
                categoryBreakdown(current, previous, currentNetSales),
                forecast.getPredictedSalesAmount(),
                null
        );
    }

    private PeriodItems load(Long storeId, LocalDate start, LocalDate end) {
        List<SalesOrderEntity> orders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay()
                ).stream()
                .filter(SalesOrderEntity::isValid)
                .toList();
        if (orders.isEmpty()) {
            return new PeriodItems(Map.of(), List.of());
        }
        Map<Long, SalesOrderEntity> ordersById = new HashMap<>();
        orders.forEach(order -> ordersById.put(order.getId(), order));
        List<SalesOrderItemEntity> menuItems = itemRepository
                .findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(ordersById.keySet())
                .stream()
                .filter(item -> item.getItemType() == SalesOrderItemType.MENU)
                .toList();
        return new PeriodItems(ordersById, menuItems);
    }

    private List<SalesSolutionMetrics.HourlyPoint> hourlyProfile(PeriodItems period) {
        Map<HourKey, Long> totals = new TreeMap<>(Comparator
                .comparing(HourKey::dayType)
                .thenComparingInt(HourKey::hour));
        for (SalesOrderItemEntity item : period.menuItems()) {
            SalesOrderEntity order = period.ordersById().get(item.getSalesOrderId());
            HourKey key = new HourKey(dayType(order.getOrderedAt().getDayOfWeek()),
                    order.getOrderedAt().getHour());
            totals.merge(key, item.getNetAmount(), Math::addExact);
        }
        return totals.entrySet().stream()
                .map(entry -> new SalesSolutionMetrics.HourlyPoint(
                        entry.getKey().dayType(),
                        entry.getKey().hour(),
                        entry.getValue()
                ))
                .toList();
    }

    private List<SalesSolutionMetrics.CategoryPoint> categoryBreakdown(
            PeriodItems current,
            PeriodItems previous,
            long currentNetSales
    ) {
        Map<SalesStandardMenuCategory, Long> currentTotals = categoryTotals(current.menuItems());
        Map<SalesStandardMenuCategory, Long> previousTotals = categoryTotals(previous.menuItems());
        List<SalesSolutionMetrics.CategoryPoint> result = new ArrayList<>();
        currentTotals.entrySet().stream()
                .sorted(Map.Entry.<SalesStandardMenuCategory, Long>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .forEach(entry -> result.add(new SalesSolutionMetrics.CategoryPoint(
                        categoryName(entry.getKey()),
                        share(entry.getValue(), currentNetSales),
                        changeRate(entry.getValue(), previousTotals.getOrDefault(entry.getKey(), 0L))
                )));
        return List.copyOf(result);
    }

    private Map<SalesStandardMenuCategory, Long> categoryTotals(List<SalesOrderItemEntity> items) {
        Map<SalesStandardMenuCategory, Long> totals = new EnumMap<>(SalesStandardMenuCategory.class);
        for (SalesOrderItemEntity item : items) {
            totals.merge(item.getStandardCategory(), item.getNetAmount(), Math::addExact);
        }
        return totals;
    }

    private Double changeRate(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(Math.abs(previous)), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double share(long amount, long total) {
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String dayType(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
                ? "WEEKEND"
                : "WEEKDAY";
    }

    private String categoryName(SalesStandardMenuCategory category) {
        return switch (category) {
            case COFFEE -> "커피";
            case NON_COFFEE -> "논커피";
            case TEA -> "티";
            case ADE -> "에이드";
            case SMOOTHIE -> "스무디";
            case BOTTLED_DRINK -> "병음료";
            case BEER -> "맥주";
            case CAKE -> "케이크";
            case BAKERY -> "베이커리";
            case FOOD -> "푸드";
            case SEASONAL -> "시즌 메뉴";
            case UNMAPPED -> "미분류";
        };
    }

    private record PeriodItems(
            Map<Long, SalesOrderEntity> ordersById,
            List<SalesOrderItemEntity> menuItems
    ) {}

    private record HourKey(String dayType, int hour) {}
}
