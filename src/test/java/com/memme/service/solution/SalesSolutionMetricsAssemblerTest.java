package com.memme.service.solution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.memme.dto.sales.MenuRankingMetric;
import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.store.MenuCategory;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.service.sales.insight.DistrictBenchmarkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesSolutionMetricsAssemblerTest {

    @Test
    void includesDistrictBenchmarkForForecastTargetMonth() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        DistrictBenchmarkService benchmarkService = mock(DistrictBenchmarkService.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 9, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 21));
        DistrictBenchmarkMetric benchmark = new DistrictBenchmarkMetric(
                "2026Q2", 16_887_880, 159, "11-14", "17-21");
        when(benchmarkService.findForInsight(10L, YearMonth.of(2026, 10)))
                .thenReturn(Optional.of(benchmark));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository,
                mock(ConditionPatternsService.class), benchmarkService)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.districtBenchmark()).isEqualTo(benchmark);
        verify(benchmarkService).findForInsight(10L, YearMonth.of(2026, 10));
    }

    @Test
    void includesConditionPatternsResolvedForTheForecastTargetDate() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        ConditionPatternsService conditionPatternsService = mock(ConditionPatternsService.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        LocalDate targetDate = LocalDate.of(2026, 10, 19);
        SalesSolutionMetrics.ConditionPattern conditionPattern = new SalesSolutionMetrics.ConditionPattern(
                "RAIN", 12, 1.0, 5, 5, 0.6, 0.8,
                List.of(new SalesSolutionMetrics.ConditionMenu("아이스 아메리카노", 4, 8,
                        -0.5, 1_000, 2_000)));
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(targetDate);
        when(forecast.getPredictedSalesAmount()).thenReturn(100_000L);
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());
        when(conditionPatternsService.resolve(10L, targetDate)).thenReturn(List.of(conditionPattern));

        SalesSolutionMetrics metrics = new SalesSolutionMetricsAssembler(
                orderRepository, itemRepository, businessHoursRepository, conditionPatternsService,
                mock(DistrictBenchmarkService.class))
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.conditionPatterns()).containsExactly(conditionPattern);
        assertThat(metrics.districtBenchmark()).isNull();
        verify(conditionPatternsService).resolve(10L, targetDate);
    }

    @Test
    void assemblesTopFiveMenuRankingsAndWeekdayWeekendBusinessHours() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 3));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 4));

        SalesOrderEntity currentOne = order(1L, LocalDateTime.of(2026, 10, 1, 10, 0));
        SalesOrderEntity currentTwo = order(2L, LocalDateTime.of(2026, 10, 2, 11, 0));
        SalesOrderEntity previous = order(3L, LocalDateTime.of(2026, 9, 30, 12, 0));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentOne, currentTwo), List.of(previous));

        List<SalesOrderItemEntity> currentItems = List.of(
                item(1L, "americano", "아메리카노", 600, 6),
                item(2L, "latte", "카페라떼", 500, 5),
                item(1L, "tea", "홍차", 400, 4),
                item(2L, "ade", "레몬에이드", 300, 3),
                item(1L, "cake", "치즈케이크", 200, 2),
                item(2L, "cookie", "쿠키", 100, 1));
        List<SalesOrderItemEntity> previousItems = List.of(
                item(3L, "americano", "아메리카노", 300, 3),
                item(3L, "latte", "카페라떼", 1_000, 10),
                item(3L, "tea", "홍차", 400, 4),
                item(3L, "ade", "레몬에이드", 300, 3),
                item(3L, "cake", "치즈케이크", 100, 1),
                item(3L, "cookie", "쿠키", 200, 2));
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(currentItems)
                .thenReturn(previousItems);
        List<StoreBusinessHours> businessHours = List.of(
                hours(1, true, null, null),
                hours(2, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(3, false, LocalTime.of(10, 0), LocalTime.of(21, 0)),
                hours(4, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(5, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(6, false, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                hours(7, false, LocalTime.of(11, 0), LocalTime.of(20, 0)));
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(businessHours);

        SalesSolutionMetrics metrics = assembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.salesSummary().netSales()).isEqualTo(2_100);
        assertThat(metrics.salesSummary().period()).isEqualTo("2026-10-01~2026-10-03");
        var mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(metrics));
        assertThat(json.path("salesSummary").path("period").asString())
                .isEqualTo("2026-10-01~2026-10-03");
        assertThat(metrics.menuRankings()).containsExactly(
                new MenuRankingMetric(1, "아메리카노", BigDecimal.valueOf(600), 6L,
                        new BigDecimal("0.2857"), new BigDecimal("1.0000"), new BigDecimal("0.1304")),
                new MenuRankingMetric(2, "카페라떼", BigDecimal.valueOf(500), 5L,
                        new BigDecimal("0.2381"), new BigDecimal("-0.5000"), new BigDecimal("0.4348")),
                new MenuRankingMetric(3, "홍차", BigDecimal.valueOf(400), 4L,
                        new BigDecimal("0.1905"), new BigDecimal("0.0000"), new BigDecimal("0.1739")),
                new MenuRankingMetric(4, "레몬에이드", BigDecimal.valueOf(300), 3L,
                        new BigDecimal("0.1429"), new BigDecimal("0.0000"), new BigDecimal("0.1304")),
                new MenuRankingMetric(5, "치즈케이크", BigDecimal.valueOf(200), 2L,
                        new BigDecimal("0.0952"), new BigDecimal("1.0000"), new BigDecimal("0.0435")));
        assertThat(metrics.businessHours()).isEqualTo(new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("08:00", "22:00"),
                new SalesSolutionMetrics.BusinessHourBand("09:00", "21:00"),
                List.of("MON")));
    }

    @Test
    void combinesTopFiveSalesAndTopFiveAbsoluteChangesUpToTenWithoutRenumberingRanks() {
        List<Long> currentAmounts = List.of(1_000L, 900L, 800L, 700L, 600L,
                500L, 400L, 300L, 200L, 100L);
        List<SalesOrderItemEntity> currentItems = IntStream.range(0, currentAmounts.size())
                .mapToObj(index -> item(1L, "menu-" + index, "메뉴 " + index,
                        currentAmounts.get(index), 10))
                .toList();
        List<SalesOrderItemEntity> previousItems = IntStream.range(0, currentAmounts.size())
                .mapToObj(index -> item(2L, "menu-" + index, "메뉴 " + index,
                        index < 5 ? currentAmounts.get(index) : 1_000L, 9))
                .toList();

        SalesSolutionMetrics metrics = assembleMenuRankings(currentItems, previousItems);

        assertThat(metrics.menuRankings()).hasSize(10);
        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::menuName)
                .containsExactly("메뉴 0", "메뉴 1", "메뉴 2", "메뉴 3", "메뉴 4",
                        "메뉴 5", "메뉴 6", "메뉴 7", "메뉴 8", "메뉴 9");
        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(metrics.menuRankings().getFirst().ratio()).isEqualByComparingTo("0.1818");
    }

    @Test
    void deduplicatesMenusThatAreInBothTopSalesAndTopChangeGroups() {
        List<Long> currentAmounts = List.of(1_000L, 900L, 800L, 700L, 600L,
                50L, 40L, 30L, 20L);
        List<SalesOrderItemEntity> currentItems = IntStream.range(0, currentAmounts.size())
                .mapToObj(index -> item(1L, "menu-" + index, "메뉴 " + index,
                        currentAmounts.get(index), 10))
                .toList();
        List<SalesOrderItemEntity> previousItems = IntStream.range(0, currentAmounts.size())
                .mapToObj(index -> item(2L, "menu-" + index, "메뉴 " + index,
                        index == 0 ? 100L : index < 5 ? currentAmounts.get(index) : 1_000L, 9))
                .toList();

        SalesSolutionMetrics metrics = assembleMenuRankings(currentItems, previousItems);

        assertThat(metrics.menuRankings()).hasSize(9);
        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::menuName)
                .containsExactly("메뉴 0", "메뉴 1", "메뉴 2", "메뉴 3", "메뉴 4",
                        "메뉴 5", "메뉴 6", "메뉴 7", "메뉴 8");
        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::menuName)
                .doesNotHaveDuplicates();
    }

    @Test
    void filtersGrowthCandidatesByThreeAverageDailyBaselineItemsAndKeepsNullPreviousRateInSalesTopFive() {
        List<Long> currentAmounts = List.of(1_000L, 900L, 800L, 700L, 600L,
                500L, 400L, 300L);
        List<SalesOrderItemEntity> currentItems = IntStream.range(0, currentAmounts.size())
                .mapToObj(index -> item(1L, "menu-" + index, "메뉴 " + index,
                        currentAmounts.get(index), 10))
                .toList();
        List<SalesOrderItemEntity> previousItems = List.of(
                item(2L, "menu-0", "메뉴 0", 1_000, 9),
                item(2L, "menu-1", "메뉴 1", 0, 9), // zero previous revenue: null rate, sales-top-five only
                item(2L, "menu-2", "메뉴 2", 800, 9),
                item(2L, "menu-3", "메뉴 3", 700, 9),
                item(2L, "menu-4", "메뉴 4", 600, 9),
                item(2L, "menu-5", "메뉴 5", 1_000, 9), // exactly 3 per day: eligible
                item(2L, "menu-6", "메뉴 6", 1_000, 8), // below 3 per day: ineligible
                item(2L, "menu-7", "메뉴 7", 300, 9));

        SalesSolutionMetrics metrics = assembleMenuRankings(currentItems, previousItems);

        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::menuName)
                .contains("메뉴 5")
                .doesNotContain("메뉴 6");
        assertThat(metrics.menuRankings().stream()
                .filter(menu -> menu.menuName().equals("메뉴 1"))
                .findFirst())
                .hasValueSatisfying(menu -> assertThat(menu.vsPrevPeriod()).isNull());
        assertThat(metrics.menuRankings().stream()
                .filter(menu -> menu.menuName().equals("메뉴 5"))
                .findFirst())
                .hasValueSatisfying(menu -> assertThat(menu.vsPrevPeriod())
                        .isEqualByComparingTo("-0.5000"));
    }

    @Test
    void keepsGlobalRankGapsAndRanksForPreviousOnlyMenus() {
        List<SalesOrderItemEntity> currentItems = IntStream.range(0, 12)
                .mapToObj(index -> item(1L, "menu-" + index, "메뉴 " + index,
                        1_200L - index * 100L, 10))
                .toList();
        List<SalesOrderItemEntity> previousItems = IntStream.range(0, 12)
                .mapToObj(index -> item(2L, "menu-" + index, "메뉴 " + index,
                        index >= 8 ? 1_000L : 1_200L - index * 100L, 9))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        previousItems.add(item(2L, "discontinued", "종료 메뉴", 900, 9));

        SalesSolutionMetrics metrics = assembleMenuRankings(currentItems, previousItems);

        assertThat(metrics.menuRankings()).extracting(MenuRankingMetric::rank)
                .containsExactly(1, 2, 3, 4, 5, 9, 10, 11, 12, 13);
        assertThat(metrics.menuRankings()).filteredOn(menu -> menu.menuName().equals("종료 메뉴"))
                .singleElement()
                .satisfies(menu -> {
                    assertThat(menu.rank()).isEqualTo(13);
                    assertThat(menu.ratio()).isEqualByComparingTo("0.0000");
                    assertThat(menu.vsPrevPeriod()).isEqualByComparingTo("-1.0000");
                    assertThat(menu.previousRatio()).isPositive();
                });
    }

    @Test
    void includesPreviousOnlyCategoryWithBothPeriodShares() {
        SalesOrderItemEntity currentCoffee = item(1L, "coffee", "커피", 900, 9);
        SalesOrderItemEntity previousCoffee = item(2L, "coffee", "커피", 500, 9);
        SalesOrderItemEntity previousTea = item(2L, "tea", "차", 500, 9);
        when(previousTea.getStandardCategory()).thenReturn(MenuCategory.TEA);

        SalesSolutionMetrics metrics = assembleMenuRankings(
                List.of(currentCoffee), List.of(previousCoffee, previousTea));

        assertThat(metrics.categoryBreakdown()).containsExactly(
                new SalesSolutionMetrics.CategoryPoint("커피", 1.0, 0.8, 0.5),
                new SalesSolutionMetrics.CategoryPoint("차", 0.0, -1.0, 0.5));
        assertThat(metrics.menuRankings()).filteredOn(menu -> menu.menuName().equals("차"))
                .singleElement().satisfies(menu ->
                        assertThat(menu.previousRatio()).isEqualByComparingTo("0.5000"));
        var mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(metrics));
        assertThat(json.path("categoryBreakdown").get(1).path("previousShare").asDouble())
                .isEqualTo(0.5);
    }

    @Test
    void omitsMenuRankingsWhenNegativeLowerRankedMenuMakesRatioExceedOne() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        SalesOrderEntity currentOrder = order(1L, LocalDateTime.of(2026, 10, 1, 10, 0));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentOrder))
                .thenReturn(List.of());
        List<SalesOrderItemEntity> currentItems = List.of(
                item(1L, "top", "인기 메뉴", 120, 12),
                item(1L, "second", "메뉴 2", 10, 1),
                item(1L, "third", "메뉴 3", 10, 1),
                item(1L, "fourth", "메뉴 4", 10, 1),
                item(1L, "fifth", "메뉴 5", 10, 1),
                item(1L, "refund", "환불 메뉴", -60, -1));
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(currentItems)
                .thenReturn(List.of());
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());

        SalesSolutionMetrics metrics = assembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.salesSummary().netSales()).isEqualTo(100);
        assertThat(metrics.menuRankings()).isNull();
    }

    @Test
    void representsEqualOpenAndCloseTimesAsTwentyFourHourBusiness() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        List<StoreBusinessHours> schedules = List.of(
                hours(1, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(2, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(3, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(4, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(5, false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
                hours(6, false, LocalTime.of(9, 0), LocalTime.MIDNIGHT),
                hours(7, false, LocalTime.of(9, 0), LocalTime.MIDNIGHT));
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(schedules);

        SalesSolutionMetrics metrics = assembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.menuRankings()).isNull();
        assertThat(metrics.businessHours()).isEqualTo(new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("00:00", "24:00"),
                new SalesSolutionMetrics.BusinessHourBand("09:00", "24:00"),
                null));
    }

    @Test
    void omitsBusinessHoursWhenNonMidnightOpenAndCloseTimesAreEqual() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 1));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        List<StoreBusinessHours> schedules = List.of(
                hours(1, false, LocalTime.of(8, 0), LocalTime.of(8, 0)),
                hours(2, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(3, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(4, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(5, false, LocalTime.of(8, 0), LocalTime.of(22, 0)),
                hours(6, false, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                hours(7, false, LocalTime.of(9, 0), LocalTime.of(21, 0)));
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(schedules);

        SalesSolutionMetrics metrics = assembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        assertThat(metrics.businessHours()).isNull();
    }

    @Test
    void excludesForecastTargetDateSalesFromSolutionMetrics() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 7, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 2));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());

        SalesSolutionMetrics metrics = assembler(
                orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);

        ArgumentCaptor<LocalDateTime> endExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, org.mockito.Mockito.times(2))
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        eq(10L), any(LocalDateTime.class), endExclusive.capture());
        assertThat(endExclusive.getAllValues().getFirst())
                .isEqualTo(LocalDate.of(2026, 10, 2).atStartOfDay());
        assertThat(metrics.salesSummary().period()).isEqualTo("2026-07-01~2026-10-01");
        assertThat(metrics.menuRankings()).isNull();
        assertThat(metrics.businessHours()).isNull();
    }

    private SalesOrderEntity order(Long id, LocalDateTime orderedAt) {
        SalesOrderEntity order = mock(SalesOrderEntity.class);
        when(order.getId()).thenReturn(id);
        when(order.isValid()).thenReturn(true);
        when(order.getOrderedAt()).thenReturn(orderedAt);
        return order;
    }

    private SalesSolutionMetricsAssembler assembler(
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            StoreBusinessHoursRepository businessHoursRepository
    ) {
        return new SalesSolutionMetricsAssembler(
                orderRepository,
                itemRepository,
                businessHoursRepository,
                mock(ConditionPatternsService.class),
                mock(DistrictBenchmarkService.class)
        );
    }

    private SalesSolutionMetrics assembleMenuRankings(
            List<SalesOrderItemEntity> currentItems,
            List<SalesOrderItemEntity> previousItems
    ) {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        StoreBusinessHoursRepository businessHoursRepository = mock(StoreBusinessHoursRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 10, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 3));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 4));
        when(forecast.getPredictedSalesAmount()).thenReturn(1_000L);
        SalesOrderEntity currentOrder = order(1L, LocalDateTime.of(2026, 10, 1, 10, 0));
        SalesOrderEntity previousOrder = order(2L, LocalDateTime.of(2026, 9, 28, 10, 0));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currentOrder))
                .thenReturn(List.of(previousOrder));
        when(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(anyCollection()))
                .thenReturn(currentItems)
                .thenReturn(previousItems);
        when(businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of());

        return assembler(orderRepository, itemRepository, businessHoursRepository)
                .assemble(10L, analysisRun, forecast);
    }

    private SalesOrderItemEntity item(Long orderId, String key, String name, long netAmount, int quantity) {
        SalesOrderItemEntity item = mock(SalesOrderItemEntity.class);
        when(item.getSalesOrderId()).thenReturn(orderId);
        when(item.getItemType()).thenReturn(SalesOrderItemType.MENU);
        when(item.getMenuKey()).thenReturn(key);
        when(item.getMenuName()).thenReturn(name);
        when(item.getNetAmount()).thenReturn(netAmount);
        when(item.getQuantity()).thenReturn(quantity);
        when(item.getStandardCategory()).thenReturn(MenuCategory.COFFEE);
        return item;
    }

    private StoreBusinessHours hours(int day, boolean closed, LocalTime opensAt, LocalTime closesAt) {
        StoreBusinessHours hours = mock(StoreBusinessHours.class);
        when(hours.getDayOfWeek()).thenReturn(day);
        when(hours.isClosed()).thenReturn(closed);
        when(hours.getOpensAt()).thenReturn(opensAt);
        when(hours.getClosesAt()).thenReturn(closesAt);
        return hours;
    }
}
