package com.memme.service.solution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.memme.dto.sales.MenuRankingMetric;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SalesSolutionMetricsAssembler {

    private static final int MAX_MENU_RANKINGS = 5;
    private static final int RATE_SCALE = 4;
    private static final DateTimeFormatter BUSINESS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final StoreBusinessHoursRepository businessHoursRepository;
    private final ConditionPatternsService conditionPatternsService;

    public SalesSolutionMetricsAssembler(
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            StoreBusinessHoursRepository businessHoursRepository,
            ConditionPatternsService conditionPatternsService
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.conditionPatternsService = conditionPatternsService;
    }

    public SalesSolutionMetrics assemble(
            Long storeId,
            AnalysisRunEntity analysisRun,
            SalesForecastEntity forecast
    ) {
        LocalDate start = analysisRun.getPeriodStart();
        LocalDate end = analysisRun.getPeriodEnd().isBefore(forecast.getTargetDate())
                ? analysisRun.getPeriodEnd()
                : forecast.getTargetDate().minusDays(1);
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
                null,
                null,
                menuRankings(current, previous, currentNetSales),
                businessHours(storeId),
                conditionPatternsService.resolve(storeId, forecast.getTargetDate())
        );
    }

    private List<MenuRankingMetric> menuRankings(
            PeriodItems current,
            PeriodItems previous,
            long currentNetSales
    ) {
        if (currentNetSales <= 0 || current.menuItems().isEmpty()) {
            return null;
        }

        Map<String, MutableMenuSales> currentTotals = menuTotals(current.menuItems());
        Map<String, MutableMenuSales> previousTotals = menuTotals(previous.menuItems());
        if (currentTotals.values().stream().anyMatch(menu -> menu.amount < 0 || menu.quantity < 0)) {
            return null;
        }
        List<MutableMenuSales> ranked = currentTotals.values().stream()
                .sorted(Comparator.comparingLong((MutableMenuSales menu) -> menu.amount).reversed()
                        .thenComparing(menu -> menu.name))
                .limit(MAX_MENU_RANKINGS)
                .toList();

        List<MenuRankingMetric> result = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            MutableMenuSales menu = ranked.get(index);
            MutableMenuSales previousMenu = previousTotals.get(menu.key);
            result.add(new MenuRankingMetric(
                    index + 1,
                    menu.name,
                    BigDecimal.valueOf(menu.amount),
                    menu.quantity,
                    ratio(menu.amount, currentNetSales),
                    decimalChangeRate(menu.amount, previousMenu == null ? 0 : previousMenu.amount)
            ));
        }
        return List.copyOf(result);
    }

    private Map<String, MutableMenuSales> menuTotals(List<SalesOrderItemEntity> items) {
        Map<String, MutableMenuSales> totals = new HashMap<>();
        for (SalesOrderItemEntity item : items) {
            MutableMenuSales menu = totals.computeIfAbsent(
                    item.getMenuKey(),
                    key -> new MutableMenuSales(key, item.getMenuName())
            );
            menu.amount = Math.addExact(menu.amount, item.getNetAmount());
            menu.quantity = Math.addExact(menu.quantity, item.getQuantity());
        }
        return totals;
    }

    private SalesSolutionMetrics.BusinessHours businessHours(Long storeId) {
        List<StoreBusinessHours> schedules = businessHoursRepository
                .findAllByStoreIdOrderByDayOfWeekAsc(storeId);
        if (schedules.isEmpty()) {
            return null;
        }

        List<String> closedDays = new ArrayList<>();
        for (StoreBusinessHours schedule : schedules) {
            Integer dayOfWeek = schedule.getDayOfWeek();
            if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
                return null;
            }
            if (Boolean.TRUE.equals(schedule.isClosed())) {
                closedDays.add(DayOfWeek.of(dayOfWeek).name().substring(0, 3));
            } else {
                if (schedule.getOpensAt() == null || schedule.getClosesAt() == null) {
                    return null;
                }
                if (schedule.getOpensAt().equals(schedule.getClosesAt())
                        && !schedule.getOpensAt().equals(LocalTime.MIDNIGHT)) {
                    return null;
                }
            }
        }

        BusinessBand weekday = mostCommonBand(schedules, true);
        BusinessBand weekend = mostCommonBand(schedules, false);
        if (weekday == null || weekend == null) {
            return null;
        }
        return new SalesSolutionMetrics.BusinessHours(
                weekday.toDto(),
                weekend.toDto(),
                closedDays.isEmpty() ? null : List.copyOf(closedDays)
        );
    }

    private BusinessBand mostCommonBand(
            List<StoreBusinessHours> schedules,
            boolean weekdays
    ) {
        Map<BusinessBand, BandFrequency> frequencies = new LinkedHashMap<>();
        for (StoreBusinessHours schedule : schedules) {
            if (Boolean.TRUE.equals(schedule.isClosed())) {
                continue;
            }
            DayOfWeek day = DayOfWeek.of(schedule.getDayOfWeek());
            boolean isWeekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
            if (isWeekday != weekdays) {
                continue;
            }
            BusinessBand band = new BusinessBand(schedule.getOpensAt(), schedule.getClosesAt());
            BandFrequency frequency = frequencies.computeIfAbsent(band, ignored -> new BandFrequency(day));
            frequency.count++;
            if (day.getValue() < frequency.firstDay.getValue()) {
                frequency.firstDay = day;
            }
        }
        return frequencies.entrySet().stream()
                .min(Comparator.<Map.Entry<BusinessBand, BandFrequency>>comparingInt(
                                entry -> entry.getValue().count
                        ).reversed()
                        .thenComparingInt(entry -> entry.getValue().firstDay.getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
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

    private BigDecimal ratio(long amount, long total) {
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalChangeRate(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO.setScale(RATE_SCALE) : null;
        }
        return BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(previous))
                .divide(BigDecimal.valueOf(previous).abs(), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private record BusinessBand(LocalTime opensAt, LocalTime closesAt) {

        private SalesSolutionMetrics.BusinessHourBand toDto() {
            if (opensAt.equals(closesAt)) {
                return new SalesSolutionMetrics.BusinessHourBand("00:00", "24:00");
            }
            return new SalesSolutionMetrics.BusinessHourBand(
                    BUSINESS_TIME_FORMAT.format(opensAt),
                    closesAt.equals(LocalTime.MIDNIGHT)
                            ? "24:00"
                            : BUSINESS_TIME_FORMAT.format(closesAt)
            );
        }
    }

    private static final class BandFrequency {

        private int count;
        private DayOfWeek firstDay;

        private BandFrequency(DayOfWeek firstDay) {
            this.firstDay = firstDay;
        }
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
