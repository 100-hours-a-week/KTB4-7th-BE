package com.memme.service.sales.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.memme.dto.sales.SalesAnalysisResponse;
import com.memme.dto.sales.SalesCategoriesResponse;
import com.memme.dto.sales.SalesPeriod;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesAnalysisService {

    private static final int RATE_SCALE = 1;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SalesDailySummaryRepository dailySummaryRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesAiInsightRepository insightRepository;

    public SalesAnalysisService(
            SalesDailySummaryRepository dailySummaryRepository,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            SalesAiInsightRepository insightRepository
    ) {
        this.dailySummaryRepository = dailySummaryRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.insightRepository = insightRepository;
    }

    public SalesAnalysisResult analyze(
            Long storeId,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        Objects.requireNonNull(storeId, "storeId");
        String normalizedPeriodType = requireText(periodType, "periodType");
        validatePeriod(periodStart, periodEnd);

        SalesPeriod period = new SalesPeriod(normalizedPeriodType, periodStart, periodEnd);
        List<SalesDailySummaryEntity> summaries = dailySummaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                        storeId,
                        periodStart,
                        periodEnd
                );
        if (summaries.isEmpty()) {
            return empty(period, aiInsight(storeId, YearMonth.from(periodEnd)));
        }

        DateRange comparisonRange = comparisonRange(normalizedPeriodType, periodStart, periodEnd);
        List<SalesDailySummaryEntity> comparisonSummaries = dailySummaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                        storeId,
                        comparisonRange.start(),
                        comparisonRange.end()
                );

        Totals current = totals(summaries);
        Totals comparison = totals(comparisonSummaries);
        List<SalesOrderEntity> orders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        periodStart.atStartOfDay(),
                        periodEnd.plusDays(1).atStartOfDay()
                );
        List<SalesOrderItemEntity> items = loadItems(orders);
        Map<Long, SalesOrderEntity> ordersById = indexOrders(orders);

        List<SalesAnalysisResponse.MenuRanking> menuRankings = menuRankings(items);
        SalesAnalysisResponse response = new SalesAnalysisResponse(
                period,
                new SalesAnalysisResponse.DateRange(comparisonRange.start(), comparisonRange.end()),
                new SalesAnalysisResponse.Kpis(
                        money(current.totalSales()),
                        current.orderCount(),
                        averageOrderValue(current),
                        new SalesAnalysisResponse.Changes(
                                changeRate(current.totalSales(), comparison.totalSales()),
                                changeRate(current.orderCount(), comparison.orderCount()),
                                changeRate(
                                        averageOrderValue(current),
                                        averageOrderValue(comparison)
                                )
                        )
                ),
                dailySales(periodStart, periodEnd, summaries),
                menuRankings,
                hourlySales(items, ordersById),
                weekdaySales(items, ordersById),
                aiInsight(storeId, YearMonth.from(periodEnd))
        );

        return new SalesAnalysisResult.Completed(response, categories(items));
    }

    private SalesAnalysisResult.Empty empty(
            SalesPeriod period,
            SalesAnalysisResponse.AiInsight aiInsight
    ) {
        return new SalesAnalysisResult.Empty(new SalesAnalysisResponse.EmptyData(
                period,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                aiInsight
        ));
    }

    private SalesAnalysisResponse.AiInsight aiInsight(Long storeId, YearMonth targetMonth) {
        return insightRepository.findByStoreIdAndTargetMonth(storeId, targetMonth)
                .filter(insight -> insight.getStatus() == SalesAiInsightStatus.COMPLETED)
                .filter(insight -> insight.getInsights() != null && !insight.getInsights().isEmpty())
                .map(insight -> new SalesAnalysisResponse.AiInsight(
                        insight.getTargetMonth(),
                        insight.getInsights(),
                        insight.getGeneratedAt().atZone(SEOUL).toOffsetDateTime()
                ))
                .orElse(null);
    }

    private List<SalesOrderItemEntity> loadItems(List<SalesOrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        return itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(
                orders.stream().map(SalesOrderEntity::getId).toList()
        );
    }

    private Map<Long, SalesOrderEntity> indexOrders(List<SalesOrderEntity> orders) {
        Map<Long, SalesOrderEntity> result = new HashMap<>();
        for (SalesOrderEntity order : orders) {
            result.put(order.getId(), order);
        }
        return result;
    }

    private Totals totals(List<SalesDailySummaryEntity> summaries) {
        long totalSales = 0;
        long orderCount = 0;
        for (SalesDailySummaryEntity summary : summaries) {
            totalSales = Math.addExact(totalSales, summary.getTotalNetAmount());
            orderCount = Math.addExact(orderCount, summary.getOrderCount());
        }
        return new Totals(totalSales, orderCount);
    }

    private List<SalesAnalysisResponse.DailySales> dailySales(
            LocalDate start,
            LocalDate end,
            List<SalesDailySummaryEntity> summaries
    ) {
        Map<LocalDate, Long> salesByDate = new HashMap<>();
        for (SalesDailySummaryEntity summary : summaries) {
            salesByDate.put(summary.getSalesDate(), summary.getTotalNetAmount());
        }
        List<SalesAnalysisResponse.DailySales> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            result.add(new SalesAnalysisResponse.DailySales(
                    date,
                    money(salesByDate.getOrDefault(date, 0L))
            ));
        }
        return List.copyOf(result);
    }

    private List<SalesAnalysisResponse.MenuRanking> menuRankings(
            List<SalesOrderItemEntity> items
    ) {
        Map<String, MutableMenuTotal> totals = new HashMap<>();
        for (SalesOrderItemEntity item : items) {
            if (item.getItemType() != SalesOrderItemType.MENU) {
                continue;
            }
            MutableMenuTotal total = totals.computeIfAbsent(
                    item.getMenuKey(),
                    ignored -> new MutableMenuTotal(item.getMenuName())
            );
            total.sales = Math.addExact(total.sales, item.getNetAmount());
            total.quantity = Math.addExact(total.quantity, item.getQuantity());
        }

        List<MutableMenuTotal> sorted = totals.values().stream()
                .sorted(Comparator.comparingLong(MutableMenuTotal::sales).reversed()
                        .thenComparing(MutableMenuTotal::menuName))
                .toList();
        List<SalesAnalysisResponse.MenuRanking> result = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            MutableMenuTotal total = sorted.get(index);
            result.add(new SalesAnalysisResponse.MenuRanking(
                    index + 1,
                    total.menuName,
                    money(total.sales),
                    total.quantity
            ));
        }
        return List.copyOf(result);
    }

    private List<SalesAnalysisResponse.HourlySales> hourlySales(
            List<SalesOrderItemEntity> items,
            Map<Long, SalesOrderEntity> ordersById
    ) {
        Map<Integer, Long> totals = new TreeMap<>();
        for (SalesOrderItemEntity item : items) {
            SalesOrderEntity order = requireOrder(item, ordersById);
            totals.merge(
                    order.getOrderedAt().getHour(),
                    item.getNetAmount(),
                    Math::addExact
            );
        }
        return totals.entrySet().stream()
                .map(entry -> new SalesAnalysisResponse.HourlySales(
                        entry.getKey(),
                        money(entry.getValue())
                ))
                .toList();
    }

    private List<SalesAnalysisResponse.WeekdaySales> weekdaySales(
            List<SalesOrderItemEntity> items,
            Map<Long, SalesOrderEntity> ordersById
    ) {
        Map<DayOfWeek, Long> totals = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            totals.put(dayOfWeek, 0L);
        }
        for (SalesOrderItemEntity item : items) {
            SalesOrderEntity order = requireOrder(item, ordersById);
            DayOfWeek dayOfWeek = order.getOrderedAt().getDayOfWeek();
            totals.put(dayOfWeek, Math.addExact(totals.get(dayOfWeek), item.getNetAmount()));
        }
        return totals.entrySet().stream()
                .map(entry -> new SalesAnalysisResponse.WeekdaySales(
                        entry.getKey(),
                        money(entry.getValue())
                ))
                .toList();
    }

    private SalesCategoriesResponse categories(List<SalesOrderItemEntity> items) {
        Map<SalesStandardMenuCategory, Long> categoryTotals = new EnumMap<>(
                SalesStandardMenuCategory.class
        );
        long menuSales = 0;
        for (SalesOrderItemEntity item : items) {
            if (item.getItemType() != SalesOrderItemType.MENU) {
                continue;
            }
            categoryTotals.merge(
                    item.getStandardCategory(),
                    item.getNetAmount(),
                    Math::addExact
            );
            menuSales = Math.addExact(menuSales, item.getNetAmount());
        }

        long totalMenuSales = menuSales;
        List<SalesCategoriesResponse.Category> categories = categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<SalesStandardMenuCategory, Long>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .map(entry -> new SalesCategoriesResponse.Category(
                        categoryName(entry.getKey()),
                        money(entry.getValue()),
                        ratio(entry.getValue(), totalMenuSales)
                ))
                .toList();

        List<SalesCategoriesResponse.MenuRanking> rankings = menuRankings(items).stream()
                .map(ranking -> new SalesCategoriesResponse.MenuRanking(
                        ranking.rank(),
                        ranking.menuName(),
                        ranking.salesAmount(),
                        ranking.quantity()
                ))
                .toList();
        return new SalesCategoriesResponse(categories, rankings);
    }

    private SalesOrderEntity requireOrder(
            SalesOrderItemEntity item,
            Map<Long, SalesOrderEntity> ordersById
    ) {
        SalesOrderEntity order = ordersById.get(item.getSalesOrderId());
        if (order == null) {
            throw new IllegalStateException("주문 항목의 상위 주문을 찾을 수 없습니다: " + item.getId());
        }
        return order;
    }

    private DateRange comparisonRange(String periodType, LocalDate start, LocalDate end) {
        return switch (periodType) {
            case "TODAY" -> new DateRange(start.minusDays(1), end.minusDays(1));
            case "THIS_WEEK" -> new DateRange(start.minusWeeks(1), end.minusWeeks(1));
            case "THIS_MONTH" -> new DateRange(start.minusMonths(1), end.minusMonths(1));
            default -> {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                LocalDate comparisonEnd = start.minusDays(1);
                yield new DateRange(comparisonEnd.minusDays(days - 1), comparisonEnd);
            }
        };
    }

    private BigDecimal averageOrderValue(Totals totals) {
        if (totals.orderCount() == 0) {
            return BigDecimal.ZERO;
        }
        return money(totals.totalSales()).divide(
                BigDecimal.valueOf(totals.orderCount()),
                0,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal changeRate(long current, long previous) {
        return changeRate(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO.setScale(RATE_SCALE) : null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long amount, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
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

    private void validatePeriod(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "periodStart");
        Objects.requireNonNull(end, "periodEnd");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("periodStart must not be after periodEnd");
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record Totals(long totalSales, long orderCount) {}

    private record DateRange(LocalDate start, LocalDate end) {}

    private static final class MutableMenuTotal {
        private final String menuName;
        private long sales;
        private long quantity;

        private MutableMenuTotal(String menuName) {
            this.menuName = menuName;
        }

        private String menuName() {
            return menuName;
        }

        private long sales() {
            return sales;
        }
    }
}
