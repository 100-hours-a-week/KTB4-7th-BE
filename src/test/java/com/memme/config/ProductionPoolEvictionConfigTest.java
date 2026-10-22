package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class ProductionPoolEvictionConfigTest {
    private static final String EXP = "production-db-cutover-v2";
    @Test void evictsOnlyOnceForAValidCommand() {
        var pool = mock(HikariDataSource.class);
        var management = mock(HikariPoolMXBean.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") HashOperations<String,Object,Object> hashes = mock(HashOperations.class);
        when(pool.getHikariPoolMXBean()).thenReturn(management);
        when(redis.opsForHash()).thenReturn(hashes);
        try (var control = new ProductionPoolEvictionConfig.PoolControl(pool, redis, EXP, Clock.fixed(java.time.Instant.ofEpochMilli(1000), java.time.ZoneOffset.UTC))) {
            var command = command("one", 6000);
            control.handle(command, 1000);
            control.handle(command, 1100);
            verify(management, times(1)).softEvictConnections();
            ArgumentCaptor<String> ackValue = ArgumentCaptor.forClass(String.class);
            verify(hashes, times(1)).put(eq("memme:prod:v2:pool-control:" + EXP + ":ack:one"),
                    anyString(), ackValue.capture());
            assertThat(ackValue.getValue()).isEqualTo("1000,0,0");
        }
    }

    @Test void writesRegistryHeartbeatAndReadsOnlyTheConfiguredCommandKey() {
        var pool = mock(HikariDataSource.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") HashOperations<String,Object,Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Map.of());
        try (var control = new ProductionPoolEvictionConfig.PoolControl(pool, redis, EXP,
                Clock.fixed(java.time.Instant.ofEpochMilli(10000), java.time.ZoneOffset.UTC))) {
            control.poll();

            verify(hashes).put(eq("memme:prod:v2:pool-control:" + EXP + ":registry"), anyString(), eq("10000"));
            verify(hashes).entries(eq("memme:prod:v2:pool-control:" + EXP + ":command"));
        }
    }
    @Test void rejectsExpiredFutureAndOtherExperimentCommands() {
        var pool = mock(HikariDataSource.class);
        var redis = mock(StringRedisTemplate.class);
        try (var control = new ProductionPoolEvictionConfig.PoolControl(pool, redis, EXP, Clock.fixed(java.time.Instant.ofEpochMilli(1000), java.time.ZoneOffset.UTC))) {
            control.handle(command("expired", 1000), 1000);
            control.handle(command("future", 12000), 1000);
            var wrong = command("wrong", 6000); wrong.put("experiment", "other");
            control.handle(wrong, 1000);
            verifyNoInteractions(pool, redis);
        }
    }
    @Test void refusesACommandThatExpiredDuringTheRedisRead() {
        var pool=mock(HikariDataSource.class);
        var management=mock(HikariPoolMXBean.class);
        when(pool.getHikariPoolMXBean()).thenReturn(management);
        var redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") HashOperations<String,Object,Object> hashes=mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        var now=new java.util.concurrent.atomic.AtomicLong(1000);
        var clock=mock(Clock.class);
        when(clock.millis()).thenAnswer(call->now.get());
        when(hashes.entries(anyString())).thenAnswer(call->{now.set(2000);return command("late",1500);});
        try(var control=new ProductionPoolEvictionConfig.PoolControl(pool,redis,EXP,clock)) {
            control.poll();
            verify(management,never()).softEvictConnections();
        }
    }
    private static Map<Object,Object> command(String nonce, long deadline) {
        var map = new HashMap<Object,Object>();
        map.put("experiment", EXP); map.put("action", "soft-evict");
        map.put("nonce", nonce); map.put("deadline", Long.toString(deadline));
        return map;
    }
}
