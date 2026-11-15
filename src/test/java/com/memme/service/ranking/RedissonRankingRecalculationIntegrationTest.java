package com.memme.service.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

/**
 * Opt-in cross-client check against an isolated standalone test Redis.
 * Separate Redisson clients model independent application instances; this is not a forked-JVM test.
 */
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_LOCK_ENABLED", matches = "true")
class RedissonRankingRecalculationIntegrationTest {

    private static RedissonClient firstClient;
    private static RedissonClient secondClient;
    private static String keyPrefix;

    @BeforeAll
    static void connectToTestRedis() {
        keyPrefix = "memme:ranking:recalculation:lock:test:" + UUID.randomUUID();
        firstClient = Redisson.create(testRedisConfig());
        secondClient = Redisson.create(testRedisConfig());
    }

    @AfterAll
    static void closeClients() {
        if (firstClient != null) {
            firstClient.shutdown();
        }
        if (secondClient != null) {
            secondClient.shutdown();
        }
    }

    @Test
    void twoIndependentClientsSerializeTheSamePeriodAndProduceSequentialOperationResults() throws Exception {
        RankingRecalculationLockExecutor firstExecutor = executor(firstClient);
        RankingRecalculationLockExecutor secondExecutor = executor(secondClient);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger revision = new AtomicInteger(0);

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = workers.submit(() -> firstExecutor.execute(period(9), () -> {
                enter(active, maximumActive);
                firstEntered.countDown();
                await(releaseFirst);
                active.decrementAndGet();
                return revision.incrementAndGet();
            }));
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> second = workers.submit(() -> {
                secondAttempted.countDown();
                return secondExecutor.execute(period(9), () -> {
                    secondEntered.countDown();
                    return revision.incrementAndGet();
                });
            });
            assertThat(secondAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            releaseFirst.countDown();
        }

        assertThat(maximumActive.get()).isEqualTo(1);
        assertThat(secondEntered.getCount()).isZero();
    }

    @Test
    void differentPeriodsCanRunAtTheSameTimeAcrossClients() throws Exception {
        RankingRecalculationLockExecutor firstExecutor = executor(firstClient);
        RankingRecalculationLockExecutor secondExecutor = executor(secondClient);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<?> first = workers.submit(() -> firstExecutor.execute(period(9), () -> {
                enter(active, maximumActive);
                bothEntered.countDown();
                await(release);
                active.decrementAndGet();
                return null;
            }));
            Future<?> second = workers.submit(() -> secondExecutor.execute(period(10), () -> {
                enter(active, maximumActive);
                bothEntered.countDown();
                await(release);
                active.decrementAndGet();
                return null;
            }));

            try {
                assertThat(bothEntered.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(maximumActive.get()).isEqualTo(2);
    }

    private static RankingRecalculationLockExecutor executor(RedissonClient client) {
        RankingRecalculationLockProperties properties = new RankingRecalculationLockProperties();
        properties.setLockProvider(RankingRecalculationLockProperties.Provider.REDIS);
        properties.setLockWaitTimeout(Duration.ofSeconds(3));
        properties.setLockWatchdogTimeout(Duration.ofSeconds(5));
        properties.setLockKeyPrefix(keyPrefix);
        return new RedissonRankingRecalculationLockExecutor(client, properties);
    }

    private static Config testRedisConfig() {
        String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
        int database = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_DATABASE", "0"));
        String username = System.getenv().getOrDefault("TEST_REDIS_LOCK_USERNAME", "");
        String password = System.getenv().getOrDefault("TEST_REDIS_LOCK_PASSWORD", "");

        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + formatHost(host) + ":" + port)
                .setDatabase(database)
                .setTimeout(2_000)
                .setConnectTimeout(2_000);
        if (!username.isBlank()) {
            server.setUsername(username);
        }
        if (!password.isBlank()) {
            server.setPassword(password);
        }
        return config;
    }

    private static String formatHost(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static void enter(AtomicInteger active, AtomicInteger maximumActive) {
        int nowActive = active.incrementAndGet();
        maximumActive.accumulateAndGet(nowActive, Math::max);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("test operation was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while holding a test lock", exception);
        }
    }

    private static RankingPeriodCalculator.PeriodPair period(int month) {
        return new RankingPeriodCalculator.PeriodPair(
                LocalDate.of(2026, month, 1),
                LocalDate.of(2026, month, 28),
                LocalDate.of(2026, month - 1, 1),
                LocalDate.of(2026, month - 1, 28)
        );
    }
}
