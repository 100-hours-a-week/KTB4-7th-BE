package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.exception.auth.LoginRateLimitExceededException;
import com.memme.repository.auth.LoginRateLimitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 12, 0);

    @Mock private LoginRateLimitRepository repository;
    @Mock private LoginRateLimitKeyHasher keyHasher;

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        limiter = new LoginRateLimiter(repository, keyHasher, clock);
        when(keyHasher.hashEmail(" Owner@Memme.com ")).thenReturn("email-hash");
        when(keyHasher.hashIp("203.0.113.8")).thenReturn("ip-hash");
    }

    @Test
    void 계정_및_IP_제한_검사에_HMAC_키와_현재시각을_전달한다() {
        when(repository.checkAllowed(eq("email-hash"), eq("ip-hash"), any())).thenReturn(Optional.empty());

        limiter.checkAllowed(" Owner@Memme.com ", "203.0.113.8");

        verify(repository).checkAllowed(eq("email-hash"), eq("ip-hash"), eq(NOW));
    }

    @Test
    void 제한_해제까지_남은_시간을_올림해_429_예외에_담는다() {
        when(repository.checkAllowed(eq("email-hash"), eq("ip-hash"), eq(NOW)))
                .thenReturn(Optional.of(NOW.plusNanos(1)));

        assertThatThrownBy(() -> limiter.checkAllowed(" Owner@Memme.com ", "203.0.113.8"))
                .isInstanceOfSatisfying(LoginRateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(1));
    }
}
