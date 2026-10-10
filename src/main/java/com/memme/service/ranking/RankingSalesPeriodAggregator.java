package com.memme.service.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

import com.memme.entity.sales.SalesDailyStatus;
import org.springframework.stereotype.Component;

@Component
public class RankingSalesPeriodAggregator {

    public OptionalLong aggregate(
            LocalDate periodStart,
            LocalDate periodEnd,
            Map<LocalDate, DailySales> dailySalesByDate,
            Set<LocalDate> confirmedClosedDates,
            List<AnalysisPeriod> completedAnalysisPeriods
    ) {
        long totalSales = 0L;
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            DailySales dailySales = dailySalesByDate.get(date);
            if (dailySales != null && dailySales.status() == SalesDailyStatus.COMPLETE) {
                if (!isAnalysisCompleted(date, completedAnalysisPeriods)) {
                    return OptionalLong.empty();
                }
                totalSales = Math.addExact(totalSales, dailySales.totalNetAmount());
                continue;
            }
            if ((dailySales != null && dailySales.status() == SalesDailyStatus.CLOSED)
                    || confirmedClosedDates.contains(date)) {
                continue;
            }
            return OptionalLong.empty();
        }
        return OptionalLong.of(totalSales);
    }

    private boolean isAnalysisCompleted(LocalDate date, List<AnalysisPeriod> completedAnalysisPeriods) {
        return completedAnalysisPeriods.stream().anyMatch(period -> period.includes(date));
    }

    public record DailySales(SalesDailyStatus status, long totalNetAmount) {

        public DailySales {
            Objects.requireNonNull(status, "status");
        }
    }

    public record AnalysisPeriod(LocalDate start, LocalDate end) {

        public AnalysisPeriod {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("analysis period start must not be after end");
            }
        }

        private boolean includes(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }
}
