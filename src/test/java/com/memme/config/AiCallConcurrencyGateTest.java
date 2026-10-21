package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AiCallConcurrencyGateTest {

    @Test
    void 한_도메인의_AI_호출이_끝나기_전까지_다른_호출은_대기한다() throws Exception {
        var gate = new AiCallConcurrencyGate();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondAttempting = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> gate.call(() -> {
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> {
                secondAttempting.countDown();
                return gate.call(() -> {
                    secondEntered.countDown();
                    return "second";
                });
            });
            assertThat(secondAttempting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(secondEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirst.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(secondEntered.getCount()).isZero();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
