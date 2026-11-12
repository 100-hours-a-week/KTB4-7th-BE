package com.memme.service.sales.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.store.MenuCategory;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderItemRepository.HourlySalesAggregate;
import com.memme.repository.sales.SalesOrderItemRepository.MenuSalesAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesAnalysisServiceTest {

    private SalesDailySummaryRepository dailySummaryRepository;
    private SalesOrderItemRepository itemRepository;
    private SalesAiInsightRepository insightRepository;
    private SalesForecastRepository forecastRepository;
    private SalesAnalysisService service;

    @BeforeEach
    void setUp() {
        dailySummaryRepository = mock(SalesDailySummaryRepository.class);
        itemRepository = mock(SalesOrderItemRepository.class);
        insightRepository = mock(SalesAiInsightRepository.class);
        forecastRepository = mock(SalesForecastRepository.class);
        service = new SalesAnalysisService(
                dailySummaryRepository,
                itemRepository,
                insightRepository,
                forecastRepository
        );
    }

    @Test
    void calculatesKpisTrendsRankingsCategoriesHoursWeekdaysAndComparison() {
        long storeId = 301L;
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 2);
        LocalDate comparisonStart = LocalDate.of(2026, 8, 1);
        LocalDate comparisonEnd = LocalDate.of(2026, 8, 2);

        SalesDailySummaryEntity firstDay = summary(start, 800, 1);
        SalesDailySummaryEntity secondDay = summary(end, 500, 1);
        SalesDailySummaryEntity comparisonDay = summary(comparisonStart, 1_000, 2);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, start, end
        )).thenReturn(List.of(firstDay, secondDay));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, comparisonStart, comparisonEnd
        )).thenReturn(List.of(comparisonDay));

        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.plusDays(1).atStartOfDay();
        when(itemRepository.aggregateMenuSales(storeId, startAt, endAt)).thenReturn(List.of(
                menuRow("americano", MenuCategory.COFFEE, 800, 1, 11L, 101L, "아메리카노"),
                menuRow("earl-grey", MenuCategory.TEA, 500, 1, 13L, 103L, "얼그레이")
        ));
        when(itemRepository.aggregateHourlySales(storeId, startAt, endAt)).thenReturn(List.of(
                hourRow(start, 9, 1_000),
                hourRow(start, 13, -200),
                hourRow(end, 13, 500)
        ));

        SalesAnalysisResult result = service.analyze(storeId, "THIS_MONTH", start, end);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            var analysis = completed.analysis();
            assertThat(analysis.comparisonPeriod().startDate()).isEqualTo(comparisonStart);
            assertThat(analysis.comparisonPeriod().endDate()).isEqualTo(comparisonEnd);
            assertThat(analysis.kpis().totalSales()).isEqualByComparingTo("1300");
            assertThat(analysis.kpis().orderCount()).isEqualTo(2);
            assertThat(analysis.kpis().averageOrderValue()).isEqualByComparingTo("650");
            assertThat(analysis.kpis().changes().totalSalesRate()).isEqualByComparingTo("30.0");
            assertThat(analysis.kpis().changes().orderCountRate()).isEqualByComparingTo("0.0");
            assertThat(analysis.kpis().changes().averageOrderValueRate())
                    .isEqualByComparingTo("30.0");
            assertThat(analysis.dailySales())
                    .extracting(daily -> daily.salesAmount().longValueExact())
                    .containsExactly(800L, 500L);
            assertThat(analysis.menuRankings())
                    .extracting(ranking -> ranking.menuName() + ":" + ranking.salesAmount()
                            + ":" + ranking.quantity())
                    .containsExactly("아메리카노:800:1", "얼그레이:500:1");
            assertThat(analysis.hourlySales())
                    .extracting(hourly -> hourly.hour() + ":" + hourly.salesAmount())
                    .containsExactly("9:1000", "13:300");
            assertThat(analysis.weekdaySales()).hasSize(7);
            assertThat(analysis.aiInsight()).isNull();
            assertThat(analysis.forecast()).isNull();

            assertThat(completed.categories().categories())
                    .extracting(category -> category.categoryName() + ":" + category.netSales()
                            + ":" + category.ratio())
                    .containsExactly("커피:800:61.5", "차:500:38.5");
        });
    }

    @Test
    void includesForecastsOverlappingTheSelectedPeriodAndTheirTotals() {
        long storeId = 301L;
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 2);
        SalesDailySummaryEntity selectedSummary = summary(start, 100, 1);
        SalesForecastEntity firstForecast = forecast(start, 1_000, 900, 1_100);
        SalesForecastEntity secondForecast = forecast(end, 2_000, 1_800, 2_200);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, start, end
        )).thenReturn(List.of(selectedSummary));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)
        )).thenReturn(List.of());
        when(forecastRepository.findAllByStoreIdAndTargetDateBetweenOrderByTargetDateAsc(
                storeId, start, end
        )).thenReturn(List.of(
                firstForecast,
                secondForecast
        ));

        SalesAnalysisResult result = service.analyze(storeId, "THIS_MONTH", start, end);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            var forecast = completed.analysis().forecast();
            assertThat(forecast.predictedSalesAmount()).isEqualByComparingTo("3000");
            assertThat(forecast.lowerBound()).isEqualByComparingTo("2700");
            assertThat(forecast.upperBound()).isEqualByComparingTo("3300");
            assertThat(forecast.dailyForecasts())
                    .extracting(daily -> daily.targetDate() + ":" + daily.predictedSalesAmount())
                    .containsExactly("2026-09-01:1000", "2026-09-02:2000");
        });
    }

    @Test
    void returnsEmptyResultWithoutLoadingOrdersWhenPeriodHasNoSalesData() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 3);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of());

        SalesAnalysisResult result = service.analyze(storeId, "TODAY", date, date);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Empty.class, empty -> {
            assertThat(empty.analysis().period().type()).isEqualTo("TODAY");
            assertThat(empty.analysis().kpis()).isNull();
            assertThat(empty.analysis().dailySales()).isEmpty();
        });
        verifyNoInteractions(itemRepository);
    }

    @Test
    void usesStoreIdInEveryRepositoryQuery() {
        long storeId = 901L;
        LocalDate date = LocalDate.of(2026, 9, 8);
        SalesDailySummaryEntity today = summary(date, 100, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of(today));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        )).thenReturn(List.of());

        service.analyze(storeId, "TODAY", date, date);

        verify(dailySummaryRepository).findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        );
        verify(dailySummaryRepository).findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        );
        verify(itemRepository).aggregateMenuSales(
                storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        verify(itemRepository).aggregateHourlySales(
                storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Test
    void comparesFullCustomMonthWithEntirePreviousCalendarMonth() {
        long storeId = 301L;
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate comparisonStart = LocalDate.of(2026, 5, 1);
        LocalDate comparisonEnd = LocalDate.of(2026, 5, 31);
        SalesDailySummaryEntity selectedSummary = summary(start, 100, 1);
        SalesDailySummaryEntity comparisonSummary = summary(comparisonStart, 120, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, start, end
        )).thenReturn(List.of(selectedSummary));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, comparisonStart, comparisonEnd
        )).thenReturn(List.of(comparisonSummary));

        SalesAnalysisResult result = service.analyze(storeId, "CUSTOM", start, end);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed ->
                assertThat(completed.analysis().comparisonPeriod())
                        .isEqualTo(new com.memme.dto.sales.SalesAnalysisResponse.DateRange(
                                comparisonStart, comparisonEnd
                        ))
        );
        verify(dailySummaryRepository).findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, comparisonStart, comparisonEnd
        );
    }

    @Test
    void keepsPreviousSameLengthComparisonForPartialCustomRange() {
        long storeId = 301L;
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 20);
        LocalDate comparisonStart = LocalDate.of(2026, 5, 30);
        LocalDate comparisonEnd = LocalDate.of(2026, 6, 9);
        SalesDailySummaryEntity selectedSummary = summary(start, 100, 1);
        SalesDailySummaryEntity comparisonSummary = summary(comparisonStart, 120, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, start, end
        )).thenReturn(List.of(selectedSummary));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, comparisonStart, comparisonEnd
        )).thenReturn(List.of(comparisonSummary));

        SalesAnalysisResult result = service.analyze(storeId, "CUSTOM", start, end);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed ->
                assertThat(completed.analysis().comparisonPeriod())
                        .isEqualTo(new com.memme.dto.sales.SalesAnalysisResponse.DateRange(
                                comparisonStart, comparisonEnd
                        ))
        );
    }

    @Test
    void returnsCompletedInsightForMonthContainingPeriodEnd() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 8);
        SalesDailySummaryEntity dailySummary = summary(date, 100, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of(dailySummary));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        )).thenReturn(List.of());

        SalesAiInsightEntity insight = mock(SalesAiInsightEntity.class);
        when(insight.getStatus()).thenReturn(SalesAiInsightStatus.COMPLETED);
        when(insight.getTargetMonth()).thenReturn(YearMonth.of(2026, 9));
        when(insight.getInsights()).thenReturn(List.of("9월 매출이 이전 기간보다 증가했습니다."));
        when(insight.getGeneratedAt()).thenReturn(LocalDateTime.of(2026, 9, 8, 10, 0));
        when(insightRepository.findByStoreIdAndTargetMonth(storeId, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));

        SalesAnalysisResult result = service.analyze(storeId, "TODAY", date, date);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            assertThat(completed.analysis().aiInsight().targetMonth())
                    .isEqualTo(YearMonth.of(2026, 9));
            assertThat(completed.analysis().aiInsight().insights())
                    .containsExactly("9월 매출이 이전 기간보다 증가했습니다.");
            assertThat(completed.analysis().aiInsight().generatedAt().getOffset().getTotalSeconds())
                    .isEqualTo(9 * 60 * 60);
        });
    }

    @Test
    void returnsFailedInsightStateWithoutFailingBaseAnalysis() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 8);
        SalesDailySummaryEntity dailySummary = summary(date, 100, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of(dailySummary));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        )).thenReturn(List.of());

        SalesAiInsightEntity insight = mock(SalesAiInsightEntity.class);
        when(insight.getStatus()).thenReturn(SalesAiInsightStatus.FAILED);
        when(insightRepository.findByStoreIdAndTargetMonth(storeId, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));

        SalesAnalysisResult result = service.analyze(storeId, "TODAY", date, date);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            var aiInsight = completed.analysis().aiInsight();
            assertThat(aiInsight).isNotNull();
            assertThat(aiInsight.status()).isEqualTo("FAILED");
            assertThat(aiInsight.helperText())
                    .isEqualTo("AI 인사이트를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            assertThat(aiInsight.insights()).isEmpty();
        });
    }

    @Test
    void returnsInsufficientInsightHelperTextWithoutFailingBaseAnalysis() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 8);
        SalesDailySummaryEntity dailySummary = summary(date, 100, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date, date
        )).thenReturn(List.of(dailySummary));
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, date.minusDays(1), date.minusDays(1)
        )).thenReturn(List.of());

        SalesAiInsightEntity insight = mock(SalesAiInsightEntity.class);
        when(insight.getStatus()).thenReturn(SalesAiInsightStatus.INSUFFICIENT_DATA);
        when(insight.getTargetMonth()).thenReturn(YearMonth.of(2026, 9));
        when(insightRepository.findByStoreIdAndTargetMonth(storeId, YearMonth.of(2026, 9)))
                .thenReturn(Optional.of(insight));

        SalesAnalysisResult result = service.analyze(storeId, "TODAY", date, date);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            var aiInsight = completed.analysis().aiInsight();
            assertThat(aiInsight.status()).isEqualTo("INSUFFICIENT_DATA");
            assertThat(aiInsight.helperText())
                    .isEqualTo("AI 인사이트를 확인하려면 최소 2주(14일) 이상의 매출 데이터가 필요합니다.");
            assertThat(aiInsight.insights()).isEmpty();
        });
    }

    @Test
    void 같은_메뉴가_여러_분류로_나뉘어도_순위는_합산하고_이름은_가장_먼저_나온_상품으로_정한다() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 7);
        stubSalesDay(storeId, date);
        when(itemRepository.aggregateMenuSales(storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        menuRow("americano", MenuCategory.COFFEE, 300, 1, 20L, 205L, "아메리카노(R)"),
                        menuRow("americano", MenuCategory.BEVERAGE, 400, 2, 12L, 130L, "아메리카노"),
                        menuRow("latte", MenuCategory.COFFEE, 700, 1, 15L, 150L, "카페라떼")
                ));

        SalesAnalysisResult result = service.analyze(storeId, "TODAY", date, date);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            assertThat(completed.analysis().menuRankings())
                    .extracting(ranking -> ranking.rank() + ":" + ranking.menuName() + ":"
                            + ranking.salesAmount() + ":" + ranking.quantity())
                    .containsExactly("1:아메리카노:700:3", "2:카페라떼:700:1");
            assertThat(completed.categories().categories())
                    .extracting(category -> category.categoryName() + ":" + category.netSales())
                    .containsExactly("커피:1000", "음료:400");
            assertThat(completed.categories().menuRankings())
                    .extracting(ranking -> ranking.menuName())
                    .containsExactly("아메리카노", "카페라떼");
        });
    }

    @Test
    void 요일별_매출은_날짜의_요일로_합산하고_월요일부터_7개를_돌려준다() {
        long storeId = 301L;
        LocalDate start = LocalDate.of(2026, 9, 7);
        LocalDate end = LocalDate.of(2026, 9, 13);
        stubSalesDay(storeId, start, end);
        when(itemRepository.aggregateHourlySales(storeId, start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        hourRow(start, 9, 100),
                        hourRow(start, 10, 20),
                        hourRow(end, 10, 50)
                ));

        SalesAnalysisResult result = service.analyze(storeId, "CUSTOM", start, end);

        assertThat(result).isInstanceOfSatisfying(SalesAnalysisResult.Completed.class, completed -> {
            assertThat(completed.analysis().weekdaySales())
                    .extracting(weekday -> weekday.dayOfWeek() + ":" + weekday.salesAmount())
                    .containsExactly("MONDAY:120", "TUESDAY:0", "WEDNESDAY:0", "THURSDAY:0",
                            "FRIDAY:0", "SATURDAY:0", "SUNDAY:50");
            assertThat(completed.analysis().hourlySales())
                    .extracting(hourly -> hourly.hour() + ":" + hourly.salesAmount())
                    .containsExactly("9:100", "10:70");
        });
    }

    @Test
    void 집계_금액이_long_범위를_넘으면_예외가_난다() {
        long storeId = 301L;
        LocalDate date = LocalDate.of(2026, 9, 7);
        stubSalesDay(storeId, date);
        when(itemRepository.aggregateMenuSales(storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(new MenuRow("americano", "COFFEE",
                        new BigDecimal("9223372036854775808"), 1, 1L, 1L, "아메리카노")));

        assertThatThrownBy(() -> service.analyze(storeId, "TODAY", date, date))
                .isInstanceOf(ArithmeticException.class);
    }

    private void stubSalesDay(long storeId, LocalDate date) {
        stubSalesDay(storeId, date, date);
    }

    private void stubSalesDay(long storeId, LocalDate start, LocalDate end) {
        SalesDailySummaryEntity salesDay = summary(start, 100, 1);
        when(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId, start, end
        )).thenReturn(List.of(salesDay));
    }

    private SalesDailySummaryEntity summary(LocalDate date, long sales, int orderCount) {
        SalesDailySummaryEntity summary = mock(SalesDailySummaryEntity.class);
        when(summary.getSalesDate()).thenReturn(date);
        when(summary.getTotalNetAmount()).thenReturn(sales);
        when(summary.getOrderCount()).thenReturn(orderCount);
        return summary;
    }

    private MenuSalesAggregate menuRow(
            String menuKey,
            MenuCategory category,
            long salesAmount,
            long quantity,
            long firstOrderId,
            long firstItemId,
            String menuName
    ) {
        return new MenuRow(menuKey, category.name(), salesAmount, quantity, firstOrderId, firstItemId, menuName);
    }

    private HourlySalesAggregate hourRow(LocalDate date, int hour, long salesAmount) {
        return new HourRow(date, hour, salesAmount);
    }

    private record MenuRow(
            String getMenuKey,
            String getStandardCategory,
            Number getSalesAmount,
            Number getQuantity,
            Long getFirstOrderId,
            Long getFirstItemId,
            String getMenuName
    ) implements MenuSalesAggregate {
    }

    private record HourRow(LocalDate getSalesDate, Number getSalesHour, Number getSalesAmount)
            implements HourlySalesAggregate {
    }

    private SalesForecastEntity forecast(
            LocalDate targetDate,
            long predictedSalesAmount,
            long lowerBound,
            long upperBound
    ) {
        return SalesForecastEntity.create(
                301L,
                11L,
                targetDate,
                targetDate.minusDays(1),
                predictedSalesAmount,
                lowerBound,
                upperBound,
                "v1",
                targetDate.atStartOfDay()
        );
    }
}
