package com.memme.service.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;

class SharedJsonCacheTest {
    @Test
    void roundTripsTypedJsonAndUsesNamespacedKeyWithTtl() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Map<String, String> saved = new HashMap<>();
        doAnswer(call -> { saved.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> saved.get(call.getArgument(0)));
        var firstNode = new SharedJsonCache(redis, true, "test:cache:v1");
        var secondNode = new SharedJsonCache(redis, true, "test:cache:v1");
        var value = new Sample(LocalDate.of(2026, 10, 28), new BigDecimal("100.25"));
        firstNode.put("sample", value, Duration.ofMinutes(5));
        assertThat(secondNode.get("sample", Sample.class)).contains(value);
        verify(values).set(eq("test:cache:v1:sample"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void bypassesRedisOutageAndCorruptJsonWithoutThrowing() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var cache = new SharedJsonCache(redis, true, "test:cache:v1");
        when(values.get(anyString())).thenReturn("{invalid json");
        assertThat(cache.get("sample", Sample.class)).isEmpty();
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("test"));
        assertThat(cache.get("sample", Sample.class)).isEmpty();
        doThrow(new RedisConnectionFailureException("test")).when(values).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> cache.put("sample", new Sample(LocalDate.now(), BigDecimal.ONE), Duration.ofMinutes(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledCacheDoesNotContactRedis() {
        var redis = mock(StringRedisTemplate.class);
        var cache = new SharedJsonCache(redis, false, "test:cache:v1");
        cache.put("sample", "value", Duration.ofMinutes(5));
        assertThat(cache.get("sample", String.class)).isEmpty();
        verifyNoInteractions(redis);
    }

    record Sample(LocalDate date, BigDecimal amount) {}
}
