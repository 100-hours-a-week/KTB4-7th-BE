package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.exception.auth.SignupRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SignupRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T00:00:00Z"));

    @Test
    void 창_안에서_한도까지는_허용하고_초과하면_남은_시간과_함께_거절한다(CapturedOutput output) {
        SignupRateLimiter limiter = new SignupRateLimiter(3, 60, 100, clock);

        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> limiter.checkAllowed("203.0.113.9")).doesNotThrowAnyException();
        }
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.9"))
                .isInstanceOfSatisfying(SignupRateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(40));
        assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.9"))
                .isInstanceOf(SignupRateLimitExceededException.class);
        assertThat(output.getOut().split("Signup rate limit exceeded", -1)).hasSize(2);
    }

    @Test
    void 창이_지나면_다시_허용한다() {
        SignupRateLimiter limiter = new SignupRateLimiter(1, 60, 100, clock);
        limiter.checkAllowed("203.0.113.9");
        assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.9"))
                .isInstanceOf(SignupRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(60));

        assertThatCode(() -> limiter.checkAllowed("203.0.113.9")).doesNotThrowAnyException();
    }

    @Test
    void IP마다_따로_센다() {
        SignupRateLimiter limiter = new SignupRateLimiter(1, 60, 100, clock);
        limiter.checkAllowed("203.0.113.9");

        assertThatCode(() -> limiter.checkAllowed("198.51.100.7")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.9"))
                .isInstanceOf(SignupRateLimitExceededException.class);
    }

    @Test
    void 추적_IP가_한도에_차면_만료된_항목을_지우고_그래도_차면_새_IP는_세지_않고_통과시킨다() {
        SignupRateLimiter limiter = new SignupRateLimiter(1, 60, 2, clock);
        limiter.checkAllowed("203.0.113.1");
        limiter.checkAllowed("203.0.113.2");

        assertThatCode(() -> limiter.checkAllowed("203.0.113.3")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed("203.0.113.3")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.1"))
                .isInstanceOf(SignupRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(60));
        limiter.checkAllowed("203.0.113.3");
        assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.3"))
                .isInstanceOf(SignupRateLimitExceededException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
