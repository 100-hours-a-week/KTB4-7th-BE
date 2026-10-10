package com.memme.service.ranking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RankingSnapshotRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(RankingSnapshotRecalculationService.class);
    private final RankingSnapshotPersistenceService persistenceService;
    private final Clock clock;

    @Autowired
    public RankingSnapshotRecalculationService(
            RankingSnapshotPersistenceService persistenceService
    ) {
        this(persistenceService, Clock.systemUTC());
    }

    RankingSnapshotRecalculationService(
            RankingSnapshotPersistenceService persistenceService,
            Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    public Result recalculateCurrentPeriod() {
        return recalculate(RankingPeriodCalculator.current(clock));
    }

    public Result recalculateIfAffected(LocalDate changedStart, LocalDate changedEnd) {
        Objects.requireNonNull(changedStart, "changedStart");
        Objects.requireNonNull(changedEnd, "changedEnd");
        if (changedStart.isAfter(changedEnd)) {
            throw new IllegalArgumentException("changedStart must not be after changedEnd");
        }

        RankingPeriodCalculator.PeriodPair period = RankingPeriodCalculator.current(clock);
        if (!overlaps(changedStart, changedEnd, period.periodStart(), period.periodEnd())
                && !overlaps(changedStart, changedEnd, period.comparisonStart(), period.comparisonEnd())) {
            return Result.SKIPPED_NOT_AFFECTED;
        }
        return recalculate(period);
    }

    private synchronized Result recalculate(RankingPeriodCalculator.PeriodPair period) {
        try {
            persistenceService.calculate(period);
            return Result.COMPLETED;
        } catch (RuntimeException exception) {
            log.error("Growth ranking snapshot calculation failed for period {}-{}", period.periodStart(),
                    period.periodEnd(), exception);
            try {
                persistenceService.recordFailed(period);
            } catch (RuntimeException recordException) {
                exception.addSuppressed(recordException);
                log.error("Could not persist failed growth ranking snapshot", recordException);
            }
            return Result.FAILED;
        }
    }

    private boolean overlaps(LocalDate firstStart, LocalDate firstEnd, LocalDate secondStart, LocalDate secondEnd) {
        return !firstEnd.isBefore(secondStart) && !firstStart.isAfter(secondEnd);
    }

    public enum Result {
        COMPLETED,
        FAILED,
        SKIPPED_NOT_AFFECTED
    }
}
