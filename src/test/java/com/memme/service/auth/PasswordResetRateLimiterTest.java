package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.exception.auth.PasswordResetRateLimitExceededException;
import com.memme.repository.auth.PasswordResetRateLimitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetRateLimiterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-02T03:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, SEOUL);

    @Mock private PasswordResetRateLimitRepository repository;
    @Mock private PasswordResetRateLimitKeyHasher keyHasher;

    private PasswordResetRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new PasswordResetRateLimiter(
                repository,
                keyHasher,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @Test
    void 허용된_요청의_정규화된_해시와_현재시각을_기록한다() {
        when(keyHasher.hashEmail("Owner@memme.com")).thenReturn("email-hash");
        when(keyHasher.hashIp("203.0.113.15")).thenReturn("ip-hash");
        when(repository.recordIfAllowed("email-hash", "ip-hash", LOCAL_NOW)).thenReturn(Optional.empty());

        rateLimiter.recordIfAllowed("Owner@memme.com", "203.0.113.15");

        verify(repository).recordIfAllowed("email-hash", "ip-hash", LOCAL_NOW);
    }

    @Test
    void 제한되면_재시도_가능_시각을_초_단위로_반환한다() {
        when(keyHasher.hashEmail("owner@memme.com")).thenReturn("email-hash");
        when(keyHasher.hashIp("203.0.113.15")).thenReturn("ip-hash");
        when(repository.recordIfAllowed("email-hash", "ip-hash", LOCAL_NOW))
                .thenReturn(Optional.of(LOCAL_NOW.plusNanos(1_200_000_000L)));

        assertThatThrownBy(() -> rateLimiter.recordIfAllowed("owner@memme.com", "203.0.113.15"))
                .isInstanceOfSatisfying(PasswordResetRateLimitExceededException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getRetryAfterSeconds())
                                .isEqualTo(2));
    }
}
