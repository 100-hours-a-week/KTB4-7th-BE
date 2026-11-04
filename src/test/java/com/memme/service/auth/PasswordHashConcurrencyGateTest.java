package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.exception.auth.PasswordHashCapacityExceededException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashConcurrencyGateTest {

    @Test
    void 두_개의_해시가_실행_중이면_추가_matches는_제한시간_후_거부된다() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean matchesCalled = new AtomicBoolean();
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                entered.countDown();
                await(release);
                return "encoded-" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                matchesCalled.set(true);
                return true;
            }
        };
        PasswordHashConcurrencyGate gate = new PasswordHashConcurrencyGate(encoder, 2, 50);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> gate.encode("first"));
            var second = executor.submit(() -> gate.encode("second"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> gate.matches("third", "encoded-third"))
                    .isInstanceOf(PasswordHashCapacityExceededException.class);
            assertThat(matchesCalled).isFalse();

            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("encoded-first");
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("encoded-second");
            assertThat(gate.matches("third", "encoded-third")).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 해시_예외가_발생해도_permit을_반환한다() {
        AtomicInteger calls = new AtomicInteger();
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("encoder failure");
                }
                return "encoded";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        };
        PasswordHashConcurrencyGate gate = new PasswordHashConcurrencyGate(encoder, 1, 0);

        assertThatThrownBy(() -> gate.encode("first"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("encoder failure");
        assertThat(gate.encode("second")).isEqualTo("encoded");
    }

    @Test
    void permit_대기_중_인터럽트되면_상태를_보존하고_거부한다() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                entered.countDown();
                await(release);
                return "encoded";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        };
        PasswordHashConcurrencyGate gate = new PasswordHashConcurrencyGate(encoder, 1, 1_000);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try {
            var first = executor.submit(() -> gate.encode("first"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            Thread waiting = new Thread(() -> {
                Thread.currentThread().interrupt();
                try {
                    gate.encode("second");
                } catch (Throwable exception) {
                    failure.set(exception);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            waiting.start();
            waiting.join(2_000);

            assertThat(waiting.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(PasswordHashCapacityExceededException.class);
            assertThat(interrupted).isTrue();
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("encoded");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
