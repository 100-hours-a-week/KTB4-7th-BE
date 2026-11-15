package com.memme.service.ranking;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.memme.exception.ranking.RankingRecalculationLockException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ranking.recalculation", name = "lock-provider", havingValue = "redis")
public class RedissonRankingRecalculationLockExecutor implements RankingRecalculationLockExecutor {

    private final RedissonClient redissonClient;
    private final RankingRecalculationLockProperties properties;

    public RedissonRankingRecalculationLockExecutor(
            RedissonClient redissonClient,
            RankingRecalculationLockProperties properties
    ) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @Override
    public <T> T execute(RankingPeriodCalculator.PeriodPair period, Supplier<T> operation) {
        RLock lock = redissonClient.getLock(lockKey(period));
        acquire(lock);

        Throwable operationFailure = null;
        try {
            return operation.get();
        } catch (RuntimeException | Error failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            release(lock, operationFailure);
        }
    }

    private void acquire(RLock lock) {
        try {
            boolean acquired = lock.tryLock(properties.getLockWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RankingRecalculationLockException(
                        RankingRecalculationLockException.Reason.ACQUISITION_TIMEOUT,
                        "Timed out waiting for the ranking recalculation lock."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RankingRecalculationLockException(
                    RankingRecalculationLockException.Reason.ACQUISITION_INTERRUPTED,
                    "Interrupted while waiting for the ranking recalculation lock.",
                    exception
            );
        } catch (RankingRecalculationLockException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RankingRecalculationLockException(
                    RankingRecalculationLockException.Reason.REDIS_UNAVAILABLE,
                    "Redis did not allow acquiring the ranking recalculation lock.",
                    exception
            );
        }
    }

    private void release(RLock lock, Throwable operationFailure) {
        try {
            if (!lock.isHeldByCurrentThread()) {
                throw new RankingRecalculationLockException(
                        RankingRecalculationLockException.Reason.OWNERSHIP_LOST,
                        "The ranking recalculation lock is no longer owned by the current thread."
                );
            }
            lock.unlock();
        } catch (RankingRecalculationLockException exception) {
            propagateReleaseFailure(exception, operationFailure);
        } catch (IllegalMonitorStateException exception) {
            propagateReleaseFailure(new RankingRecalculationLockException(
                    RankingRecalculationLockException.Reason.OWNERSHIP_LOST,
                    "The ranking recalculation lock ownership was lost before release.",
                    exception
            ), operationFailure);
        } catch (RuntimeException exception) {
            propagateReleaseFailure(new RankingRecalculationLockException(
                    RankingRecalculationLockException.Reason.RELEASE_FAILED,
                    "Redis failed while releasing the ranking recalculation lock.",
                    exception
            ), operationFailure);
        }
    }

    private void propagateReleaseFailure(
            RankingRecalculationLockException releaseFailure,
            Throwable operationFailure
    ) {
        if (operationFailure != null) {
            operationFailure.addSuppressed(releaseFailure);
            return;
        }
        throw releaseFailure;
    }

    String lockKey(RankingPeriodCalculator.PeriodPair period) {
        return properties.getLockKeyPrefix() + ":"
                + period.periodStart() + ":"
                + period.periodEnd() + ":"
                + period.comparisonStart() + ":"
                + period.comparisonEnd();
    }
}
