package com.memme.service.ranking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

class RankingSnapshotRecalculationServiceTest {

    private RankingSnapshotPersistenceService persistenceService;
    private RankingSnapshotRecalculationService service;

    @BeforeEach
    void setUp() {
        persistenceService = mock(RankingSnapshotPersistenceService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneOffset.UTC);
        service = new RankingSnapshotRecalculationService(persistenceService, clock);
    }

    @Test
    void recalculatesPreviousFullMonthAgainstTheMonthBeforeIt() {
        RankingSnapshotRecalculationService.Result result = service.recalculateCurrentPeriod();

        assertThat(result).isEqualTo(RankingSnapshotRecalculationService.Result.COMPLETED);
        verify(persistenceService).calculate(new RankingPeriodCalculator.PeriodPair(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        ));
    }

    @Test
    void serializesConcurrentRecalculationsUntilFirstPersistenceReturns() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch allowFirstToFinish = new CountDownLatch(1);
        CountDownLatch secondSubmitted = new CountDownLatch(1);
        CountDownLatch secondEnteredCalculation = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        doAnswer(invocation -> {
            int nowActive = active.incrementAndGet();
            maximumActive.accumulateAndGet(nowActive, Math::max);
            try {
                if (nowActive == 1 && firstEntered.getCount() > 0) {
                    firstEntered.countDown();
                    if (!allowFirstToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("first calculation did not resume");
                    }
                } else {
                    secondEnteredCalculation.countDown();
                }
            } finally {
                active.decrementAndGet();
            }
            return null;
        }).when(persistenceService).calculate(any());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RankingSnapshotRecalculationService.Result> first = executor.submit(service::recalculateCurrentPeriod);
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<RankingSnapshotRecalculationService.Result> second = executor.submit(() -> {
                secondSubmitted.countDown();
                return service.recalculateCurrentPeriod();
            });
            assertThat(secondSubmitted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondEnteredCalculation.await(200, TimeUnit.MILLISECONDS)).isFalse();
            allowFirstToFinish.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(RankingSnapshotRecalculationService.Result.COMPLETED);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(RankingSnapshotRecalculationService.Result.COMPLETED);
        } finally {
            allowFirstToFinish.countDown();
        }
        assertThat(maximumActive.get()).isEqualTo(1);
    }

    @Test
    void recordsFailedRevisionWhenTheCalculationFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("calculation failed"))
                .when(persistenceService).calculate(any());

        RankingSnapshotRecalculationService.Result result = service.recalculateCurrentPeriod();

        assertThat(result).isEqualTo(RankingSnapshotRecalculationService.Result.FAILED);
        verify(persistenceService).recordFailed(any());
    }

    @Test
    void recalculatesOnlyWhenAnUploadOverlapsEitherRankingMonth() {
        RankingSnapshotRecalculationService.Result unrelated = service.recalculateIfAffected(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)
        );
        RankingSnapshotRecalculationService.Result relevant = service.recalculateIfAffected(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12)
        );

        assertThat(unrelated).isEqualTo(RankingSnapshotRecalculationService.Result.SKIPPED_NOT_AFFECTED);
        assertThat(relevant).isEqualTo(RankingSnapshotRecalculationService.Result.COMPLETED);
        verify(persistenceService, org.mockito.Mockito.times(1)).calculate(any());
    }

}
