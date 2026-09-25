package com.memme.service.sales.insight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.memme.dto.sales.CategorySalesMetric;
import com.memme.dto.sales.HourlySalesMetric;
import com.memme.dto.sales.MenuRankingMetric;
import com.memme.dto.sales.SalesInsightDayType;
import com.memme.dto.sales.SalesInsightMetrics;
import com.memme.dto.sales.SalesSummaryMetric;
import com.memme.dto.sales.SalesTrendMetric;
import com.memme.dto.sales.WeekdaySalesMetric;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SalesInsightMetricsAssembler {

    static final int MINIMUM_SALES_DAYS = 14;
    private static final int MAX_MENU_RANKINGS = 10;
    private static final int RATE_SCALE = 4;

    private final SalesDailySummaryRepository dailySummaryRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;

    public SalesInsightMetricsAssembler(
            SalesDailySummaryRepository dailySummaryRepository,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository
    ) {
        this.dailySummaryRepository = dailySummaryRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
    }

    public Result assemble(Long storeId, YearMonth targetMonth) {
        MonthData current = loadMonth(storeId, targetMonth);
        MonthData previous = loadMonth(storeId, targetMonth.minusMonths(1));
        Totals currentTotals = totals(current.summaries());
        Totals previousTotals = totals(previous.summaries());

        SalesInsightMetrics metrics = new SalesInsightMetrics(
                new SalesSummaryMetric(
                        money(currentTotals.totalSales()),
                        money(currentTotals.menuSales()),
                        currentTotals.orderCount(),
                        averageOrderValue(currentTotals),
                        changeRate(currentTotals.totalSales(), previousTotals.totalSales())
                ),
                salesTrend(current.summaries()),
                weekdaySales(current.summaries()),
                hourlySales(current),
                categorySales(current, previous, currentTotals.menuSales()),
                menuRankings(current, previous, currentTotals.menuSales())
        );
        return new Result(metrics, current.summaries().size() >= MINIMUM_SALES_DAYS);
    }

    private MonthData loadMonth(Long storeId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<SalesDailySummaryEntity> summaries = dailySummaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(storeId, start, end);
        List<SalesOrderEntity> orders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay()
                ).stream()
                .filter(SalesOrderEntity::isValid)
                .toList();
        List<SalesOrderItemEntity> items = orders.isEmpty()
                ? List.of()
                : itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(
                        orders.stream().map(SalesOrderEntity::getId).toList()
                );
        Map<Long, SalesOrderEntity> ordersById = new HashMap<>();
        for (SalesOrderEntity order : orders) {
            ordersById.put(order.getId(), order);
        }
        return new MonthData(summaries, ordersById, items);
    }

    private Totals totals(List<SalesDailySummaryEntity> summaries) {
        long totalSales = 0;
        long menuSales = 0;
        long orderCount = 0;
        for (SalesDailySummaryEntity summary : summaries) {
            totalSales = Math.addExact(totalSales, summary.getTotalNetAmount());
            menuSales = Math.addExact(menuSales, summary.getMenuNetAmount());
            orderCount = Math.addExact(orderCount, summary.getOrderCount());
        }
        return new Totals(totalSales, menuSales, orderCount);
    }

    private List<SalesTrendMetric> salesTrend(List<SalesDailySummaryEntity> summaries) {
        return summaries.stream()
                .map(summary -> new SalesTrendMetric(
                        summary.getSalesDate(),
                        money(summary.getMenuNetAmount()),
                        (long) summary.getOrderCount()
                ))
                .toList();
    }

    private List<WeekdaySalesMetric> weekdaySales(List<SalesDailySummaryEntity> summaries) {
        Map<DayOfWeek, MutableSales> totals = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            totals.put(day, new MutableSales());
        }
        for (SalesDailySummaryEntity summary : summaries) {
            MutableSales sales = totals.get(summary.getSalesDate().getDayOfWeek());
            sales.amount = Math.addExact(sales.amount, summary.getMenuNetAmount());
            sales.orderCount = Math.addExact(sales.orderCount, summary.getOrderCount());
        }
        return totals.entrySet().stream()
                .map(entry -> new WeekdaySalesMetric(
                        entry.getKey(),
                        money(entry.getValue().amount),
                        entry.getValue().orderCount
                ))
                .toList();
    }

    private List<HourlySalesMetric> hourlySales(MonthData current) {
        Map<HourBucket, MutableSales> totals = new TreeMap<>();
        Map<HourBucket, Set<Long>> ordersByBucket = new HashMap<>();
        for (SalesOrderItemEntity item : current.items()) {
            if (item.getItemType() != SalesOrderItemType.MENU) {
                continue;
            }
            SalesOrderEntity order = current.ordersById().get(item.getSalesOrderId());
            if (order == null) {
                continue;
            }
            HourBucket bucket = new HourBucket(
                    dayType(order.getOrderedAt().getDayOfWeek()),
                    order.getOrderedAt().getHour()
            );
            MutableSales sales = totals.computeIfAbsent(bucket, ignored -> new MutableSales());
            sales.amount = Math.addExact(sales.amount, item.getNetAmount());
            ordersByBucket.computeIfAbsent(bucket, ignored -> new HashSet<>()).add(order.getId());
        }
        return totals.entrySet().stream()
                .map(entry -> new HourlySalesMetric(
                        entry.getKey().dayType(),
                        entry.getKey().hour(),
                        money(entry.getValue().amount),
                        (long) ordersByBucket.get(entry.getKey()).size()
                ))
                .toList();
    }

    private List<CategorySalesMetric> categorySales(
            MonthData current,
            MonthData previous,
            long totalMenuSales
    ) {
        Map<SalesStandardMenuCategory, Long> currentTotals = categoryTotals(current.items());
        Map<SalesStandardMenuCategory, Long> previousTotals = categoryTotals(previous.items());
        return currentTotals.entrySet().stream()
                .sorted(Map.Entry.<SalesStandardMenuCategory, Long>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .map(entry -> new CategorySalesMetric(
                        categoryName(entry.getKey()),
                        money(entry.getValue()),
                        ratio(entry.getValue(), totalMenuSales),
                        changeRate(entry.getValue(), previousTotals.getOrDefault(entry.getKey(), 0L))
                ))
                .toList();
    }

    private Map<SalesStandardMenuCategory, Long> categoryTotals(List<SalesOrderItemEntity> items) {
        Map<SalesStandardMenuCategory, Long> totals = new EnumMap<>(SalesStandardMenuCategory.class);
        for (SalesOrderItemEntity item : items) {
            if (item.getItemType() == SalesOrderItemType.MENU) {
                totals.merge(item.getStandardCategory(), item.getNetAmount(), Math::addExact);
            }
        }
        return totals;
    }

    private List<MenuRankingMetric> menuRankings(
            MonthData current,
            MonthData previous,
            long totalMenuSales
    ) {
        Map<String, MutableMenuSales> currentTotals = menuTotals(current.items());
        Map<String, MutableMenuSales> previousTotals = menuTotals(previous.items());
        List<MutableMenuSales> sorted = currentTotals.values().stream()
                .sorted(Comparator.comparingLong(MutableMenuSales::amount).reversed()
                        .thenComparing(MutableMenuSales::name))
                .limit(MAX_MENU_RANKINGS)
                .toList();
        List<MenuRankingMetric> rankings = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            MutableMenuSales menu = sorted.get(index);
            MutableMenuSales previousMenu = previousTotals.get(menu.key);
            rankings.add(new MenuRankingMetric(
                    index + 1,
                    menu.name,
                    money(menu.amount),
                    menu.quantity,
                    ratio(menu.amount, totalMenuSales),
                    changeRate(menu.amount, previousMenu == null ? 0 : previousMenu.amount)
            ));
        }
        return List.copyOf(rankings);
    }

    private Map<String, MutableMenuSales> menuTotals(List<SalesOrderItemEntity> items) {
        Map<String, MutableMenuSales> totals = new HashMap<>();
        for (SalesOrderItemEntity item : items) {
            if (item.getItemType() != SalesOrderItemType.MENU) {
                continue;
            }
            MutableMenuSales menu = totals.computeIfAbsent(
                    item.getMenuKey(),
                    key -> new MutableMenuSales(key, item.getMenuName())
            );
            menu.amount = Math.addExact(menu.amount, item.getNetAmount());
            menu.quantity = Math.addExact(menu.quantity, item.getQuantity());
        }
        return totals;
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
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO.setScale(RATE_SCALE) : null;
        }
        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(Math.abs(previous)), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long amount, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }

    private SalesInsightDayType dayType(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
                ? SalesInsightDayType.WEEKEND
                : SalesInsightDayType.WEEKDAY;
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

    public record Result(SalesInsightMetrics metrics, boolean sufficientData) {}

    private record Totals(long totalSales, long menuSales, long orderCount) {}

    private record MonthData(
            List<SalesDailySummaryEntity> summaries,
            Map<Long, SalesOrderEntity> ordersById,
            List<SalesOrderItemEntity> items
    ) {}

    private record HourBucket(SalesInsightDayType dayType, int hour)
            implements Comparable<HourBucket> {
        @Override
        public int compareTo(HourBucket other) {
            int dayComparison = dayType.compareTo(other.dayType);
            return dayComparison != 0 ? dayComparison : Integer.compare(hour, other.hour);
        }
    }

    private static final class MutableSales {
        private long amount;
        private long orderCount;
    }

    private static final class MutableMenuSales {
        private final String key;
        private final String name;
        private long amount;
        private long quantity;

        private MutableMenuSales(String key, String name) {
            this.key = key;
            this.name = name;
        }

        private long amount() {
            return amount;
        }

        private String name() {
            return name;
        }
    }
}
