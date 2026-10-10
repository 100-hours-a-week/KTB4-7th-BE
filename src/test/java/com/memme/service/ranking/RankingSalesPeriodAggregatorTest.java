package com.memme.service.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import com.memme.entity.sales.SalesDailyStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingSalesPeriodAggregatorTest {

    private final RankingSalesPeriodAggregator aggregator = new RankingSalesPeriodAggregator();

    @Test
    void sumsCompletedSalesAndZeroSalesWhileExcludingConfirmedClosedDays() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 3);

        OptionalLong total = aggregator.aggregate(
                start,
                end,
                Map.of(
                        start, new RankingSalesPeriodAggregator.DailySales(SalesDailyStatus.COMPLETE, 100L),
                        start.plusDays(1), new RankingSalesPeriodAggregator.DailySales(SalesDailyStatus.CLOSED, 0L),
                        end, new RankingSalesPeriodAggregator.DailySales(SalesDailyStatus.COMPLETE, 0L)
                ),
                Set.of(),
                List.of(new RankingSalesPeriodAggregator.AnalysisPeriod(start, end))
        );

        assertThat(total).hasValue(100L);
    }

    @Test
    void treatsUnknownMissingAndUnrecordedOpenDaysAsIncomplete() {
        LocalDate date = LocalDate.of(2026, 9, 1);

        for (Map<LocalDate, RankingSalesPeriodAggregator.DailySales> summaries : List.of(
                Map.of(date, new RankingSalesPeriodAggregator.DailySales(SalesDailyStatus.UNKNOWN, 0L)),
                Map.of(date, new RankingSalesPeriodAggregator.DailySales(SalesDailyStatus.MISSING, 0L)),
                Map.<LocalDate, RankingSalesPeriodAggregator.DailySales>of()
        )) {
            OptionalLong total = aggregator.aggregate(
                    date,
                    date,
                    summaries,
                    Set.of(),
                    List.of(new RankingSalesPeriodAggregator.AnalysisPeriod(date, date))
            );

            assertThat(total).isEmpty();
        }
    }

    @Test
    void excludesCompleteDayWhenItsAnalysisHasNotCompleted() {
        LocalDate date = LocalDate.of(2026, 9, 1);

        OptionalLong total = aggregator.aggregate(
                date,
                date,
                Map.of(date, new RankingSalesPeriodAggregator.DailySales(SalesDailyStatus.COMPLETE, 100L)),
                Set.of(),
                List.of()
        );

        assertThat(total).isEmpty();
    }

    @Test
    void unknownOrMissingDayRemainsIncompleteEvenWhenBusinessScheduleConfirmsClosure() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        for (SalesDailyStatus status : List.of(SalesDailyStatus.UNKNOWN, SalesDailyStatus.MISSING)) {
            OptionalLong total = aggregator.aggregate(
                    date,
                    date,
                    Map.of(date, new RankingSalesPeriodAggregator.DailySales(status, 0L)),
                    Set.of(date),
                    List.of()
            );
            assertThat(total).isEmpty();
        }
    }
}
