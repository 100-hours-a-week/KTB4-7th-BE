package com.memme.service.sales.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.cache.SharedJsonCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SalesStoreDataCacheTest {
    private final Map<String, Object> saved = new ConcurrentHashMap<>();
    private SharedJsonCache cache;
    private SalesUploadRepository uploads;
    private SalesOrderRepository orders;
    private SalesStoreDataCache storeCache;
    private List<Object[]> uploadRows;

    @BeforeEach
    void setUp() {
        cache = mock(SharedJsonCache.class);
        uploads = mock(SalesUploadRepository.class);
        orders = mock(SalesOrderRepository.class);
        when(cache.isEnabled()).thenReturn(true);
        when(cache.get(anyString(), any())).thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        doAnswer(call -> saved.put(call.getArgument(0), call.getArgument(1)))
                .when(cache).put(anyString(), any(), any(Duration.class));
        uploadRows = new ArrayList<>();
        uploadRows.add(new Object[] {1L, "COMPLETED", null, "2026-11-01T10:00"});
        when(uploads.findVersionRowsByStoreId(7L)).thenAnswer(call -> uploadRows);
        when(orders.findMaximumIdByStoreId(7L)).thenReturn(Optional.of(100L));
        storeCache = new SalesStoreDataCache(cache, uploads, orders);
    }

    @Test
    void appliedItemCountIsLoadedOnceWhileVersionIsUnchanged() {
        AtomicInteger loads = new AtomicInteger();

        long first = storeCache.appliedItemCount(7L, () -> { loads.incrementAndGet(); return 31L; });
        long second = storeCache.appliedItemCount(7L, () -> { loads.incrementAndGet(); return 99L; });

        assertThat(first).isEqualTo(31L);
        assertThat(second).isEqualTo(31L);
        assertThat(loads).hasValue(1);
    }

    @Test
    void uploadRowChangeInvalidatesCachedCount() {
        storeCache.appliedItemCount(7L, () -> 31L);
        uploadRows.set(0, new Object[] {1L, "COMPLETED", null, "2026-11-02T10:00"});

        assertThat(storeCache.appliedItemCount(7L, () -> 12L)).isEqualTo(12L);
    }

    @Test
    void newOrderIdInvalidatesCachedCount() {
        storeCache.appliedItemCount(7L, () -> 31L);
        when(orders.findMaximumIdByStoreId(7L)).thenReturn(Optional.of(101L));

        assertThat(storeCache.appliedItemCount(7L, () -> 40L)).isEqualTo(40L);
    }

    @Test
    void uploadMonthsRoundTripThroughCache() {
        List<Object[]> loaded = List.of(new Object[] {1L, 2026, 10}, new Object[] {2L, 2026, 11});
        storeCache.uploadMonths(7L, () -> loaded);

        List<Object[]> cached = storeCache.uploadMonths(7L, () -> List.of());

        assertThat(cached).hasSize(2);
        assertThat(cached.get(0)).containsExactly(1L, 2026, 10);
        assertThat(cached.get(1)).containsExactly(2L, 2026, 11);
    }

    @Test
    void disabledCacheAlwaysLoads() {
        when(cache.isEnabled()).thenReturn(false);
        AtomicInteger loads = new AtomicInteger();

        storeCache.appliedItemCount(7L, () -> { loads.incrementAndGet(); return 1L; });
        storeCache.appliedItemCount(7L, () -> { loads.incrementAndGet(); return 1L; });

        assertThat(loads).hasValue(2);
    }

    @Test
    void ttlIsSpreadBetweenFourAndSixMinutes() {
        for (int i = 0; i < 200; i++) {
            assertThat(SalesStoreDataCache.ttl().toSeconds()).isBetween(240L, 360L);
        }
    }
}
