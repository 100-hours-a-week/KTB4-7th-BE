package com.memme.service.ranking;

import java.util.function.Supplier;

/** Executes one ranking recalculation while holding the configured cross-instance lock. */
public interface RankingRecalculationLockExecutor {

    <T> T execute(RankingPeriodCalculator.PeriodPair period, Supplier<T> operation);
}
