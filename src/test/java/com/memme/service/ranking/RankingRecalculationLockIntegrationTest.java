package com.memme.service.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.ranking.RankingSnapshotStatus;
import com.memme.repository.ranking.RankingRecalculationLockRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.service.ranking.RankingGrowthCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class RankingRecalculationLockIntegrationTest {

    private static final int TEST_YEAR = ThreadLocalRandom.current().nextInt(2300, 9999);
    private static final RankingPeriodCalculator.PeriodPair PERIOD = testPeriod(1);
    private static final RankingPeriodCalculator.PeriodPair HISTORY_PERIOD = testPeriod(2);
    private static final RankingPeriodCalculator.PeriodPair FAILED_PERIOD = testPeriod(3);
    private static final RankingPeriodCalculator.PeriodPair SAME_JVM_PERIOD = testPeriod(4);
    private static final RankingPeriodCalculator.PeriodPair DIFFERENT_PERIOD_A = testPeriod(5);
    private static final RankingPeriodCalculator.PeriodPair DIFFERENT_PERIOD_B = testPeriod(6);

    @Autowired
    private RankingRecalculationLockService lockService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RankingSnapshotPersistenceService persistenceService;

    @Autowired
    private RankingSnapshotRepository snapshotRepository;

    @MockitoSpyBean
    private RankingGrowthCalculator growthCalculator;

    @Test
    void createsTheFirstPeriodLockRowAndSerializesTransactions() throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondLockAttempted = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                acquire(PERIOD);
                firstLockAcquired.countDown();
                await(releaseFirstTransaction);
            }));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                secondLockAttempted.countDown();
                acquire(PERIOD);
                secondLockAcquired.countDown();
            }));
            assertThat(secondLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondLockAcquired.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstTransaction.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstTransaction.countDown();
        }

        assertThat(lockRowCount(PERIOD)).isEqualTo(1);
    }

    @Test
    void concurrentSameJvmCalculationsWithoutSnapshotsUseSequentialRevisions() throws Exception {
        assertThat(snapshotsFor(SAME_JVM_PERIOD)).isEmpty();
        assertThat(lockRowCount(SAME_JVM_PERIOD)).isZero();

        CountDownLatch firstReachedRanking = new CountDownLatch(1);
        CountDownLatch releaseFirstCalculation = new CountDownLatch(1);
        CountDownLatch secondReachedRanking = new CountDownLatch(1);
        AtomicInteger rankingCallCount = new AtomicInteger();
        AtomicInteger activeRankingCalls = new AtomicInteger();
        AtomicInteger maximumActiveRankingCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int callNumber = rankingCallCount.incrementAndGet();
            int active = activeRankingCalls.incrementAndGet();
            maximumActiveRankingCalls.accumulateAndGet(active, Math::max);
            try {
                if (callNumber == 1) {
                    firstReachedRanking.countDown();
                    await(releaseFirstCalculation);
                } else {
                    secondReachedRanking.countDown();
                }
                return invocation.callRealMethod();
            } finally {
                activeRankingCalls.decrementAndGet();
            }
        }).when(growthCalculator).rank(anyList());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> persistenceService.calculate(SAME_JVM_PERIOD));
            assertThat(firstReachedRanking.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> persistenceService.calculate(SAME_JVM_PERIOD));
            assertThat(secondReachedRanking.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstCalculation.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(secondReachedRanking.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstCalculation.countDown();
        }

        assertThat(maximumActiveRankingCalls.get()).isEqualTo(1);
        assertThat(snapshotsFor(SAME_JVM_PERIOD))
                .extracting(RankingSnapshotEntity::getRevisionNo)
                .containsExactly(1, 2);
        assertThat(snapshotsFor(SAME_JVM_PERIOD))
                .extracting(RankingSnapshotEntity::getStatus)
                .containsExactly(RankingSnapshotStatus.COMPLETED, RankingSnapshotStatus.COMPLETED);
    }

    @Test
    void differentPeriodsAcquireIndependentLockRows() throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                acquire(DIFFERENT_PERIOD_A);
                firstLockAcquired.countDown();
                await(releaseFirstTransaction);
            }));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                acquire(DIFFERENT_PERIOD_B);
                secondLockAcquired.countDown();
            }));
            assertThat(secondLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            releaseFirstTransaction.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstTransaction.countDown();
        }

        assertThat(lockRowCount(DIFFERENT_PERIOD_A)).isEqualTo(1);
        assertThat(lockRowCount(DIFFERENT_PERIOD_B)).isEqualTo(1);
    }

    @Test
    void calculateAndFailedHistoryUseTheSamePeriodLockAndSequentialRevisions() {
        persistenceService.calculate(HISTORY_PERIOD);
        persistenceService.recordFailed(HISTORY_PERIOD);

        assertThat(snapshotsFor(HISTORY_PERIOD))
                .extracting(RankingSnapshotEntity::getRevisionNo)
                .containsExactly(1, 2);
        assertThat(snapshotsFor(HISTORY_PERIOD))
                .extracting(RankingSnapshotEntity::getStatus)
                .containsExactly(RankingSnapshotStatus.COMPLETED, RankingSnapshotStatus.FAILED);
        assertThat(lockRowCount(HISTORY_PERIOD)).isEqualTo(1);
    }

    @Test
    void failedCalculationRollsBackPersistsFailedRevisionAndAllowsSubsequentCalculation() {
        AtomicInteger rankingCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (rankingCalls.getAndIncrement() == 0) {
                throw new IllegalStateException("injected calculation failure");
            }
            return invocation.callRealMethod();
        }).when(growthCalculator).rank(anyList());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> persistenceService.calculate(FAILED_PERIOD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected calculation failure");
        persistenceService.recordFailed(FAILED_PERIOD);
        persistenceService.calculate(FAILED_PERIOD);

        assertThat(snapshotsFor(FAILED_PERIOD))
                .extracting(RankingSnapshotEntity::getRevisionNo)
                .containsExactly(1, 2);
        assertThat(snapshotsFor(FAILED_PERIOD))
                .extracting(RankingSnapshotEntity::getStatus)
                .containsExactly(RankingSnapshotStatus.FAILED, RankingSnapshotStatus.COMPLETED);
        assertThat(lockRowCount(FAILED_PERIOD)).isEqualTo(1);
    }

    @Test
    void periodLookupUsesPessimisticWrite() throws NoSuchMethodException {
        Lock lock = RankingRecalculationLockRepository.class.getMethod(
                "findByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEnd",
                LocalDate.class,
                LocalDate.class,
                LocalDate.class,
                LocalDate.class
        ).getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private void acquire(RankingPeriodCalculator.PeriodPair period) {
        lockService.acquire(period);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("first transaction was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while holding the period lock", exception);
        }
    }

    private int lockRowCount(RankingPeriodCalculator.PeriodPair period) {
        return jdbcTemplate.queryForObject("""
                select count(*) from ranking_recalculation_locks
                where period_start = ? and period_end = ?
                  and comparison_start = ? and comparison_end = ?
                """, Integer.class,
                period.periodStart(), period.periodEnd(), period.comparisonStart(), period.comparisonEnd());
    }

    private java.util.List<RankingSnapshotEntity> snapshotsFor(RankingPeriodCalculator.PeriodPair period) {
        return snapshotRepository.findAll().stream()
                .filter(snapshot -> snapshot.getPeriodStart().equals(period.periodStart()))
                .filter(snapshot -> snapshot.getPeriodEnd().equals(period.periodEnd()))
                .filter(snapshot -> snapshot.getComparisonStart().equals(period.comparisonStart()))
                .filter(snapshot -> snapshot.getComparisonEnd().equals(period.comparisonEnd()))
                .sorted(java.util.Comparator.comparingInt(RankingSnapshotEntity::getRevisionNo))
                .toList();
    }

    private static RankingPeriodCalculator.PeriodPair testPeriod(int month) {
        YearMonth period = YearMonth.of(TEST_YEAR, month);
        YearMonth comparison = period.minusMonths(1);
        return new RankingPeriodCalculator.PeriodPair(
                period.atDay(1),
                period.atEndOfMonth(),
                comparison.atDay(1),
                comparison.atEndOfMonth()
        );
    }
}
