package com.memme.service.solution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.solution.WeatherObservationEntity;
import com.memme.entity.store.Store;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.solution.WeatherObservationRepository;
import com.memme.repository.store.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConditionPatternsService {

    private static final int BASIS_WEEKS = 12;
    private static final int DAYS_PER_WEEK = 7;
    private static final int MIN_DAYS_PER_CONDITION = 5;
    private static final int MIN_BASELINE_AVERAGE_QUANTITY = 3;
    private static final int MAX_MENUS = 3;
    private static final int RATIO_SCALE = 4;
    private static final BigDecimal RAIN_THRESHOLD_MM = new BigDecimal("1.0");
    private static final BigDecimal DRY_THRESHOLD_MM = new BigDecimal("0.1");

    private final StoreRepository storeRepository;
    private final WeatherObservationRepository observationRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;

    public ConditionPatternsService(
            StoreRepository storeRepository,
            WeatherObservationRepository observationRepository,
            SalesDailySummaryRepository dailySummaryRepository,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository
    ) {
        this.storeRepository = storeRepository;
        this.observationRepository = observationRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
    }

    public List<SalesSolutionMetrics.ConditionPattern> resolve(Long storeId, LocalDate targetDate) {
        if (storeId == null || targetDate == null) {
            return null;
        }

        Long stationId = storeRepository.findById(storeId)
                .map(Store::getWeatherStationId)
                .orElse(null);
        if (stationId == null) {
            return null;
        }

        DateRange dateRange = latestCompleteWeeks(targetDate);
        List<WeatherObservationEntity> observations = observationRepository
                .findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                        stationId, dateRange.start(), dateRange.end());
        if (observations.isEmpty()) {
            return null;
        }

        Map<LocalDate, SalesDailySummaryEntity> salesDays = dailySummaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                        storeId, dateRange.start(), dateRange.end())
                .stream()
                .collect(Collectors.toMap(
                        SalesDailySummaryEntity::getSalesDate,
                        summary -> summary,
                        (first, ignored) -> first
                ));
        ClassifiedDays classifiedDays = classifyDays(observations, salesDays, dateRange);
        if (classifiedDays.rainDays().size() < MIN_DAYS_PER_CONDITION
                || classifiedDays.dryDays().size() < MIN_DAYS_PER_CONDITION) {
            return null;
        }

        List<SalesOrderEntity> orders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        dateRange.start().atStartOfDay(),
                        dateRange.end().plusDays(1).atStartOfDay())
                .stream()
                .filter(SalesOrderEntity::isValid)
                .toList();
        if (orders.isEmpty()) {
            return null;
        }

        Map<Long, LocalDate> datesByOrderId = new HashMap<>();
        orders.forEach(order -> datesByOrderId.put(order.getId(), order.getOrderedAt().toLocalDate()));
        Set<Long> orderIds = datesByOrderId.keySet();
        Map<String, MenuSales> menuSales = collectMenuSales(
                itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(orderIds),
                datesByOrderId,
                dateRange
        );

        List<SalesSolutionMetrics.ConditionMenu> menus = menuSales.entrySet().stream()
                .map(entry -> toConditionMenu(
                        entry.getKey(), entry.getValue(), classifiedDays.rainDays(), classifiedDays.dryDays()))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing((ConditionMenuCandidate candidate) ->
                                candidate.vsBaseline().abs()).reversed()
                        .thenComparing(candidate -> candidate.menu().menuName())
                        .thenComparing(ConditionMenuCandidate::menuKey))
                .limit(MAX_MENUS)
                .map(ConditionMenuCandidate::menu)
                .toList();
        if (menus.isEmpty()) {
            return null;
        }

        SalesSolutionMetrics.ConditionPattern pattern = new SalesSolutionMetrics.ConditionPattern(
                "RAIN",
                BASIS_WEEKS,
                RAIN_THRESHOLD_MM.doubleValue(),
                classifiedDays.rainDays().size(),
                classifiedDays.dryDays().size(),
                weekdayRatio(classifiedDays.rainDays()),
                weekdayRatio(classifiedDays.dryDays()),
                menus,
                averageNetSales(salesDays, classifiedDays.rainDays()),
                averageNetSales(salesDays, classifiedDays.dryDays())
        );
        return List.of(pattern);
    }

    private long averageNetSales(Map<LocalDate, SalesDailySummaryEntity> salesDays, List<LocalDate> dates) {
        long total = dates.stream()
                .map(salesDays::get)
                .mapToLong(SalesDailySummaryEntity::getMenuNetAmount)
                .reduce(0L, Math::addExact);
        return averageAsLong(total, dates.size());
    }

    private DateRange latestCompleteWeeks(LocalDate targetDate) {
        LocalDate lastCompleteSunday = targetDate.minusDays(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate firstDay = lastCompleteSunday.minusDays(BASIS_WEEKS * DAYS_PER_WEEK - 1L);
        return new DateRange(firstDay, lastCompleteSunday);
    }

    private ClassifiedDays classifyDays(
            List<WeatherObservationEntity> observations,
            Map<LocalDate, SalesDailySummaryEntity> salesDays,
            DateRange dateRange
    ) {
        List<LocalDate> rainDays = new ArrayList<>();
        List<LocalDate> dryDays = new ArrayList<>();
        for (WeatherObservationEntity observation : observations) {
            LocalDate date = observation.getObservationDate();
            if (date.isBefore(dateRange.start()) || date.isAfter(dateRange.end())) {
                continue;
            }
            SalesDailySummaryEntity dailySummary = salesDays.get(date);
            if (dailySummary == null || dailySummary.getDayStatus() != SalesDailyStatus.COMPLETE) {
                continue;
            }
            BigDecimal precipitation = observation.getPrecipitationMm();
            if (precipitation == null || precipitation.signum() < 0) {
                continue;
            }
            if (precipitation.compareTo(RAIN_THRESHOLD_MM) >= 0) {
                rainDays.add(date);
            } else if (precipitation.compareTo(DRY_THRESHOLD_MM) < 0) {
                dryDays.add(date);
            }
        }
        return new ClassifiedDays(List.copyOf(rainDays), List.copyOf(dryDays));
    }

    private Map<String, MenuSales> collectMenuSales(
            List<SalesOrderItemEntity> items,
            Map<Long, LocalDate> datesByOrderId,
            DateRange dateRange
    ) {
        Map<String, MenuSales> totalsByMenu = new TreeMap<>();
        for (SalesOrderItemEntity item : items) {
            if (item.getItemType() != SalesOrderItemType.MENU) {
                continue;
            }
            LocalDate saleDate = datesByOrderId.get(item.getSalesOrderId());
            if (saleDate == null || saleDate.isBefore(dateRange.start()) || saleDate.isAfter(dateRange.end())) {
                continue;
            }
            MenuSales menu = totalsByMenu.computeIfAbsent(item.getMenuKey(), ignored ->
                    new MenuSales(item.getMenuName()));
            menu.add(saleDate, item.getQuantity(), item.getNetAmount());
        }
        return totalsByMenu;
    }

    private Optional<ConditionMenuCandidate> toConditionMenu(
            String menuKey,
            MenuSales menuSales,
            List<LocalDate> rainDays,
            List<LocalDate> dryDays
    ) {
        Totals rainTotals = menuSales.totalFor(rainDays);
        Totals dryTotals = menuSales.totalFor(dryDays);
        if (rainTotals.quantity() < 0 || dryTotals.quantity() <= 0
                || rainTotals.amount() < 0 || dryTotals.amount() < 0
                || dryTotals.quantity() < (long) MIN_BASELINE_AVERAGE_QUANTITY * dryDays.size()) {
            return Optional.empty();
        }

        BigDecimal rateNumerator = BigDecimal.valueOf(rainTotals.quantity())
                .multiply(BigDecimal.valueOf(dryDays.size()))
                .subtract(BigDecimal.valueOf(dryTotals.quantity()).multiply(BigDecimal.valueOf(rainDays.size())));
        BigDecimal rateDenominator = BigDecimal.valueOf(dryTotals.quantity())
                .multiply(BigDecimal.valueOf(rainDays.size()));
        BigDecimal vsBaseline = rateNumerator.divide(rateDenominator, RATIO_SCALE, RoundingMode.HALF_UP);
        int averageQuantity = averageAsInt(rainTotals.quantity(), rainDays.size());
        int baselineAverageQuantity = averageAsInt(dryTotals.quantity(), dryDays.size());
        long averageSales = averageAsLong(rainTotals.amount(), rainDays.size());
        long baselineAverageSales = averageAsLong(dryTotals.amount(), dryDays.size());
        if (averageQuantity < 0 || baselineAverageQuantity <= 0 || averageSales < 0 || baselineAverageSales < 0) {
            return Optional.empty();
        }

        SalesSolutionMetrics.ConditionMenu menu = new SalesSolutionMetrics.ConditionMenu(
                menuSales.name(),
                averageQuantity,
                baselineAverageQuantity,
                vsBaseline.doubleValue(),
                averageSales,
                baselineAverageSales
        );
        return Optional.of(new ConditionMenuCandidate(menuKey, menu, vsBaseline));
    }

    private int averageAsInt(long total, int days) {
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private long averageAsLong(long total, int days) {
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private double weekdayRatio(List<LocalDate> dates) {
        long weekdays = dates.stream()
                .filter(date -> date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue())
                .count();
        return BigDecimal.valueOf(weekdays)
                .divide(BigDecimal.valueOf(dates.size()), RATIO_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record DateRange(LocalDate start, LocalDate end) {}

    private record ClassifiedDays(List<LocalDate> rainDays, List<LocalDate> dryDays) {}

    private record Totals(long quantity, long amount) {}

    private record ConditionMenuCandidate(
            String menuKey,
            SalesSolutionMetrics.ConditionMenu menu,
            BigDecimal vsBaseline
    ) {}

    private static final class MenuSales {

        private final String name;
        private final Map<LocalDate, Totals> totalsByDate = new LinkedHashMap<>();

        private MenuSales(String name) {
            this.name = name;
        }

        private void add(LocalDate date, int quantity, long amount) {
            Totals previous = totalsByDate.getOrDefault(date, new Totals(0, 0));
            totalsByDate.put(date, new Totals(
                    Math.addExact(previous.quantity(), quantity),
                    Math.addExact(previous.amount(), amount)
            ));
        }

        private Totals totalFor(List<LocalDate> dates) {
            long quantity = 0;
            long amount = 0;
            for (LocalDate date : dates) {
                Totals daily = totalsByDate.get(date);
                if (daily != null) {
                    quantity = Math.addExact(quantity, daily.quantity());
                    amount = Math.addExact(amount, daily.amount());
                }
            }
            return new Totals(quantity, amount);
        }

        private String name() {
            return name;
        }
    }
}
