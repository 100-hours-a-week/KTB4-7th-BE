package com.memme.service.ranking;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Leaves cross-instance coordination to the existing MySQL lock in persistence transactions. */
@Component
@ConditionalOnProperty(prefix = "ranking.recalculation", name = "lock-provider", havingValue = "mysql", matchIfMissing = true)
public class NoOpRankingRecalculationLockExecutor implements RankingRecalculationLockExecutor {

    @Override
    public <T> T execute(RankingPeriodCalculator.PeriodPair period, Supplier<T> operation) {
        return operation.get();
    }
}
