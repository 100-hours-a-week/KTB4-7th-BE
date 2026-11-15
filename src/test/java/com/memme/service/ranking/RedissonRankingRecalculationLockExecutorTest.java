package com.memme.service.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.memme.exception.ranking.RankingRecalculationLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

class RedissonRankingRecalculationLockExecutorTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final RankingRecalculationLockProperties properties = new RankingRecalculationLockProperties();
    private final RedissonRankingRecalculationLockExecutor executor;

    RedissonRankingRecalculationLockExecutorTest() {
        properties.setLockWaitTimeout(Duration.ofMillis(250));
        executor = new RedissonRankingRecalculationLockExecutor(redissonClient, properties);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void usesStablePeriodKeysAndRunsOnlyAfterAcquisition() throws Exception {
        RankingPeriodCalculator.PeriodPair period = period(9);
        RankingPeriodCalculator.PeriodPair otherPeriod = period(10);
        List<String> events = new ArrayList<>();
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(lock.tryLock(250L, TimeUnit.MILLISECONDS)).thenAnswer(invocation -> {
            events.add("acquire");
            return true;
        });
        when(lock.isHeldByCurrentThread()).thenAnswer(invocation -> {
            events.add("ownership-check");
            return true;
        });
        doAnswer(invocation -> {
            events.add("unlock");
            return null;
        }).when(lock).unlock();

        assertThat(executor.execute(period, () -> {
            events.add("first-operation");
            return "first";
        })).isEqualTo("first");
        assertThat(executor.execute(period, () -> {
            events.add("second-operation");
            return "second";
        })).isEqualTo("second");
        assertThat(executor.execute(otherPeriod, () -> {
            events.add("third-operation");
            return "third";
        })).isEqualTo("third");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, org.mockito.Mockito.times(3)).getLock(keys.capture());
        List<String> capturedKeys = keys.getAllValues();
        assertThat(capturedKeys.get(0)).isEqualTo(capturedKeys.get(1));
        assertThat(capturedKeys.get(2)).isNotEqualTo(capturedKeys.get(0));

        assertThat(events).containsExactly(
                "acquire", "first-operation", "ownership-check", "unlock",
                "acquire", "second-operation", "ownership-check", "unlock",
                "acquire", "third-operation", "ownership-check", "unlock"
        );
        verify(lock, org.mockito.Mockito.never())
                .tryLock(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsAcquisitionTimeoutWithoutRunningOrUnlocking() throws Exception {
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(lock.tryLock(250L, TimeUnit.MILLISECONDS)).thenReturn(false);
        AtomicBoolean operationRan = new AtomicBoolean();

        assertThatThrownBy(() -> executor.execute(period(9), () -> {
            operationRan.set(true);
            return "unexpected";
        }))
                .isInstanceOf(RankingRecalculationLockException.class)
                .satisfies(exception -> assertThat(((RankingRecalculationLockException) exception).getReason())
                        .isEqualTo(RankingRecalculationLockException.Reason.ACQUISITION_TIMEOUT));

        assertThat(operationRan).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    void restoresInterruptFlagAndDoesNotRunOperationWhenAcquisitionIsInterrupted() throws Exception {
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        doThrow(new InterruptedException("test interrupt"))
                .when(lock).tryLock(250L, TimeUnit.MILLISECONDS);
        AtomicBoolean operationRan = new AtomicBoolean();

        assertThatThrownBy(() -> executor.execute(period(9), () -> {
            operationRan.set(true);
            return "unexpected";
        }))
                .isInstanceOf(RankingRecalculationLockException.class)
                .satisfies(exception -> assertThat(((RankingRecalculationLockException) exception).getReason())
                        .isEqualTo(RankingRecalculationLockException.Reason.ACQUISITION_INTERRUPTED));

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(operationRan).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    void failsClosedWhenRedisCannotAcquireTheLock() throws Exception {
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(lock.tryLock(250L, TimeUnit.MILLISECONDS)).thenThrow(new RedisException("redis unavailable"));
        AtomicBoolean operationRan = new AtomicBoolean();

        assertThatThrownBy(() -> executor.execute(period(9), () -> {
            operationRan.set(true);
            return "unexpected";
        }))
                .isInstanceOf(RankingRecalculationLockException.class)
                .satisfies(exception -> assertThat(((RankingRecalculationLockException) exception).getReason())
                        .isEqualTo(RankingRecalculationLockException.Reason.REDIS_UNAVAILABLE));

        assertThat(operationRan).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    void releasesTheLockWhenTheProtectedOperationThrows() throws Exception {
        RuntimeException failure = new IllegalStateException("calculation failed");
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(lock.tryLock(250L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> executor.execute(period(9), () -> {
            throw failure;
        })).isSameAs(failure);

        verify(lock).unlock();
    }

    @Test
    void reportsOwnershipLossInsteadOfUnlockingAThirdPartyLock() throws Exception {
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(lock.tryLock(250L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        assertThatThrownBy(() -> executor.execute(period(9), () -> "completed"))
                .isInstanceOf(RankingRecalculationLockException.class)
                .satisfies(exception -> assertThat(((RankingRecalculationLockException) exception).getReason())
                        .isEqualTo(RankingRecalculationLockException.Reason.OWNERSHIP_LOST));

        verify(lock, never()).unlock();
    }

    @Test
    void reportsRedisFailureWhileReleasingAnOwnedLock() throws Exception {
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(lock.tryLock(250L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenThrow(new RedisException("redis unavailable"));

        assertThatThrownBy(() -> executor.execute(period(9), () -> "completed"))
                .isInstanceOf(RankingRecalculationLockException.class)
                .satisfies(exception -> assertThat(((RankingRecalculationLockException) exception).getReason())
                        .isEqualTo(RankingRecalculationLockException.Reason.RELEASE_FAILED));

        verify(lock, never()).unlock();
    }

    private RankingPeriodCalculator.PeriodPair period(int month) {
        return new RankingPeriodCalculator.PeriodPair(
                LocalDate.of(2026, month, 1),
                LocalDate.of(2026, month, 28),
                LocalDate.of(2026, month - 1, 1),
                LocalDate.of(2026, month - 1, 28)
        );
    }
}
