package com.memme.service.ranking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public final class RankingPeriodCalculator {

    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");

    private RankingPeriodCalculator() {
    }

    public static PeriodPair current(Clock clock) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(RANKING_ZONE));
        YearMonth selectedMonth = currentMonth.minusMonths(1);
        YearMonth comparisonMonth = currentMonth.minusMonths(2);
        return new PeriodPair(
                selectedMonth.atDay(1),
                selectedMonth.atEndOfMonth(),
                comparisonMonth.atDay(1),
                comparisonMonth.atEndOfMonth()
        );
    }

    public record PeriodPair(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate comparisonStart,
            LocalDate comparisonEnd
    ) {
    }
}
