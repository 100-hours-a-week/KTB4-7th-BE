package com.memme.service.ranking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import com.memme.exception.ranking.RankingRecalculationLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RankingSnapshotRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(RankingSnapshotRecalculationService.class);
    private static final String EXECUTOR = executorId();
    private final RankingSnapshotPersistenceService persistenceService;
    private final RankingRecalculationLockExecutor lockExecutor;
    private final Clock clock;

    @Autowired
    public RankingSnapshotRecalculationService(
            RankingSnapshotPersistenceService persistenceService,
            RankingRecalculationLockExecutor lockExecutor
    ) {
        this(persistenceService, lockExecutor, Clock.systemUTC());
    }

    RankingSnapshotRecalculationService(
            RankingSnapshotPersistenceService persistenceService,
            Clock clock
    ) {
        this(persistenceService, new NoOpRankingRecalculationLockExecutor(), clock);
    }

    RankingSnapshotRecalculationService(
            RankingSnapshotPersistenceService persistenceService,
            RankingRecalculationLockExecutor lockExecutor,
            Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.lockExecutor = lockExecutor;
        this.clock = clock;
    }

    public Result recalculateCurrentPeriod() {
        return recalculate(RankingPeriodCalculator.current(clock), "MONTHLY_SCHEDULER");
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
        return recalculate(period, "SALES_UPLOAD");
    }

    private Result recalculate(RankingPeriodCalculator.PeriodPair period, String source) {
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        log.info("Ranking recalculation started: source={}, executor={}, periodStart={}, periodEnd={}, comparisonStart={}, comparisonEnd={}, startedAt={}",
                source, EXECUTOR, period.periodStart(), period.periodEnd(), period.comparisonStart(),
                period.comparisonEnd(), startedAt);
        Result result;
        try {
            result = lockExecutor.execute(period, () -> recalculateWhileLocked(period, source));
        } catch (RankingRecalculationLockException exception) {
            log.error("Ranking recalculation lock failed: source={}, executor={}, periodStart={}, periodEnd={}, reason={}",
                    source, EXECUTOR, period.periodStart(), period.periodEnd(), exception.getReason(), exception);
            result = Result.LOCK_FAILED;
        }
        log.info("Ranking recalculation finished: source={}, executor={}, periodStart={}, periodEnd={}, startedAt={}, finishedAt={}, durationMs={}, result={}",
                source, EXECUTOR, period.periodStart(), period.periodEnd(), startedAt, clock.instant(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos), result);
        return result;
    }

    private Result recalculateWhileLocked(RankingPeriodCalculator.PeriodPair period, String source) {
        try {
            persistenceService.calculate(period);
            return Result.COMPLETED;
        } catch (RuntimeException exception) {
            log.error("Ranking recalculation failed: source={}, executor={}, periodStart={}, periodEnd={}",
                    source, EXECUTOR, period.periodStart(), period.periodEnd(), exception);
            try {
                persistenceService.recordFailed(period);
            } catch (RuntimeException recordException) {
                exception.addSuppressed(recordException);
                log.error("Could not persist failed growth ranking snapshot", recordException);
            }
        }
        return Result.FAILED;
    }

    private static String executorId() {
        String hostname = System.getenv("HOSTNAME");
        return (hostname == null || hostname.isBlank() ? "unknown" : hostname) + ":" + ProcessHandle.current().pid();
    }

    private boolean overlaps(LocalDate firstStart, LocalDate firstEnd, LocalDate secondStart, LocalDate secondEnd) {
        return !firstEnd.isBefore(secondStart) && !firstStart.isAfter(secondEnd);
    }

    public enum Result {
        COMPLETED,
        FAILED,
        LOCK_FAILED,
        SKIPPED_NOT_AFFECTED
    }
}
