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
    private final Map<String, String> saved = new HashMap<>();
    private SalesAnalysisQueryService service;
    private final LocalDate date = LocalDate.of(2026, 10, 28);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> saved.get(call.getArgument(0)));
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

    private SalesAnalysisResult.Completed completed() {
        return new SalesAnalysisResult.Completed(new SalesAnalysisResponse(new SalesPeriod("TODAY", date, date),
                null, null, List.of(), List.of(), List.of(), List.of(), null, null),
                new SalesCategoriesResponse(List.of(), List.of()));
    }
}
