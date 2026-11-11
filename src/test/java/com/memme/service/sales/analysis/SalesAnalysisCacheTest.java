package com.memme.service.sales.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.memme.dto.sales.*;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.cache.SharedJsonCache;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;

class SalesAnalysisCacheTest {
    private final SalesAnalysisService analysis = mock(SalesAnalysisService.class);
    private final StoreOwnershipRepository owners = mock(StoreOwnershipRepository.class);
    private final SalesAnalysisCacheVersion versions = mock(SalesAnalysisCacheVersion.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> saved = new ConcurrentHashMap<>();
    private SalesAnalysisQueryService service;
    private final LocalDate date = LocalDate.of(2026, 10, 28);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> saved.get((String) call.getArgument(0)));
        doAnswer(call -> { saved.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(owners.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(versions.read(301L, date, date)).thenReturn("version-a");
        when(analysis.analyze(301L, "TODAY", date, date)).thenReturn(completed());
        service = new SalesAnalysisQueryService(analysis, mock(SalesDailySummaryRepository.class), owners,
                Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                new SharedJsonCache(redis, true, "test:cache:v1"), versions);
    }

    @Test
    void repeatedReadUsesTypedCachedResultButStillChecksAuthorization() {
        var first = service.query(7L, 301L, "TODAY", null, null);
        assertThat(service.query(7L, 301L, "TODAY", null, null)).isEqualTo(first);
        verify(analysis, times(1)).analyze(301L, "TODAY", date, date);
        verify(owners, times(2)).existsActiveStoreOwnedBy(301L, 7L);
        assertThatThrownBy(() -> service.query(8L, 301L, "TODAY", null, null))
                .isInstanceOf(SalesAnalysisRequestException.class);
        verify(versions, times(2)).read(301L, date, date);
    }

    @Test
    void committedVersionChangeDoesNotReuseOldKeyEvenWhenItStillExists() {
        service.query(7L, 301L, "TODAY", null, null);
        when(versions.read(301L, date, date)).thenReturn("version-b");
        service.query(7L, 301L, "TODAY", null, null);
        verify(analysis, times(2)).analyze(301L, "TODAY", date, date);
        assertThat(saved).hasSize(2);
    }

    @Test
    void isolatesStoreAndPeriodKeys() {
        service.query(7L, 301L, "TODAY", null, null);
        when(owners.existsActiveStoreOwnedBy(302L, 9L)).thenReturn(true);
        when(versions.read(302L, date, date)).thenReturn("version-a");
        when(analysis.analyze(302L, "TODAY", date, date)).thenReturn(completed());
        service.query(9L, 302L, "TODAY", null, null);
        when(analysis.analyze(301L, "CUSTOM", date, date)).thenReturn(completed());
        service.query(7L, 301L, "CUSTOM", date.toString(), date.toString());
        assertThat(saved).hasSize(3);
    }

    @Test
    void redisOutageStillReturnsOriginalAnalysis() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("test"));
        doThrow(new RedisConnectionFailureException("test")).when(values).set(anyString(), anyString(), any(Duration.class));
        assertThat(service.query(7L, 301L, "TODAY", null, null)).isEqualTo(completed());
    }

    @Test
    void doesNotCacheEmptyResponsesOrAnalysisFailures() {
        when(analysis.analyze(301L, "TODAY", date, date)).thenReturn(new SalesAnalysisResult.Empty(null));
        service.query(7L, 301L, "TODAY", null, null);
        assertThat(saved).isEmpty();
        when(analysis.analyze(301L, "TODAY", date, date)).thenThrow(new IllegalStateException("test"));
        assertThatThrownBy(() -> service.query(7L, 301L, "TODAY", null, null)).isInstanceOf(IllegalStateException.class);
        assertThat(saved).isEmpty();
    }

    @Test
    void concurrentMissesForSameKeyAnalyzeOnlyOnce() throws Exception {
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(analysis.analyze(301L, "TODAY", date, date)).thenAnswer(call -> {
            leaderStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            return completed();
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<SalesAnalysisResult> leader = pool.submit(() -> service.query(7L, 301L, "TODAY", null, null));
            assertThat(leaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            List<Future<SalesAnalysisResult>> waiters = new ArrayList<>();
            for (int i = 0; i < 3; i++) waiters.add(pool.submit(() -> service.query(7L, 301L, "TODAY", null, null)));
            Thread.sleep(200);
            release.countDown();
            assertThat(leader.get(5, TimeUnit.SECONDS)).isEqualTo(completed());
            for (var waiter : waiters) assertThat(waiter.get(5, TimeUnit.SECONDS)).isEqualTo(completed());
        } finally {
            pool.shutdownNow();
        }
        verify(analysis, times(1)).analyze(301L, "TODAY", date, date);
        verify(owners, times(4)).existsActiveStoreOwnedBy(301L, 7L);
    }

    @Test
    void waiterAnalyzesItselfWhenInFlightLeaderFails() throws Exception {
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(analysis.analyze(301L, "TODAY", date, date)).thenAnswer(call -> {
            leaderStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new IllegalStateException("leader failed");
        }).thenReturn(completed());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SalesAnalysisResult> leader = pool.submit(() -> service.query(7L, 301L, "TODAY", null, null));
            assertThat(leaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<SalesAnalysisResult> waiter = pool.submit(() -> service.query(7L, 301L, "TODAY", null, null));
            Thread.sleep(200);
            release.countDown();
            assertThatThrownBy(() -> leader.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(waiter.get(5, TimeUnit.SECONDS)).isEqualTo(completed());
        } finally {
            pool.shutdownNow();
        }
        verify(analysis, times(2)).analyze(301L, "TODAY", date, date);
    }

    @Test
    void cacheTtlIsSpreadBetweenFourAndSixMinutes() {
        service.query(7L, 301L, "TODAY", null, null);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(anyString(), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofMinutes(4), Duration.ofMinutes(6));
        Set<Duration> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Duration value = SalesAnalysisQueryService.cacheTtl();
            assertThat(value).isBetween(Duration.ofMinutes(4), Duration.ofMinutes(6));
            seen.add(value);
        }
        assertThat(seen.size()).isGreaterThan(10);
    }

    private SalesAnalysisResult.Completed completed() {
        return new SalesAnalysisResult.Completed(new SalesAnalysisResponse(new SalesPeriod("TODAY", date, date),
                null, null, List.of(), List.of(), List.of(), List.of(), null, null),
                new SalesCategoriesResponse(List.of(), List.of()));
    }
}
