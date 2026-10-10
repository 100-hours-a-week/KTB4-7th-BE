package com.memme.service.ranking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPeriodCalculatorTest {

    @Test
    void usesPreviousAndPriorFullMonthsAtTheKoreanMonthBoundary() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:30:00Z"), ZoneOffset.UTC);

        RankingPeriodCalculator.PeriodPair period = RankingPeriodCalculator.current(clock);

        assertThat(period.periodStart()).hasToString("2026-09-01");
        assertThat(period.periodEnd()).hasToString("2026-09-30");
        assertThat(period.comparisonStart()).hasToString("2026-08-01");
        assertThat(period.comparisonEnd()).hasToString("2026-08-31");
    }

    @Test
    void movesAcrossYearBoundaryUsingFullCalendarMonths() {
        Clock clock = Clock.fixed(Instant.parse("2026-12-31T15:30:00Z"), ZoneOffset.UTC);

        RankingPeriodCalculator.PeriodPair period = RankingPeriodCalculator.current(clock);

        assertThat(period.periodStart()).hasToString("2026-12-01");
        assertThat(period.periodEnd()).hasToString("2026-12-31");
        assertThat(period.comparisonStart()).hasToString("2026-11-01");
        assertThat(period.comparisonEnd()).hasToString("2026-11-30");
    }
}
