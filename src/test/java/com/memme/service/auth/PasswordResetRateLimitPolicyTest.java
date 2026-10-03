package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordResetRateLimitPolicyTest {

    private final PasswordResetRateLimitPolicy policy = new PasswordResetRateLimitPolicy();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 2, 12, 0);

    @Test
    void 기존_요청이_없으면_허용한다() {
        assertThat(policy.retryAt(List.of(), List.of(), now)).isEmpty();
    }

    @Test
    void 이메일_요청_후_2분이_지나지_않았으면_재시도_시각을_반환한다() {
        LocalDateTime lastAttempt = now.minusSeconds(30);

        assertThat(policy.retryAt(List.of(lastAttempt), List.of(), now))
                .contains(lastAttempt.plusMinutes(2));
    }

    @Test
    void 이메일_최근_24시간_요청이_5회이면_가장_오래된_유효_요청의_만료를_기다린다() {
        List<LocalDateTime> emailAttempts = List.of(
                now.minusHours(1), now.minusHours(2), now.minusHours(3), now.minusHours(4), now.minusHours(5)
        );

        assertThat(policy.retryAt(emailAttempts, List.of(), now))
                .contains(now.minusHours(5).plusHours(24));
    }

    @Test
    void 이메일_요청이_5회_미만이고_2분이_지났으면_허용한다() {
        List<LocalDateTime> emailAttempts = List.of(now.minusMinutes(3), now.minusHours(1));

        assertThat(policy.retryAt(emailAttempts, List.of(), now)).isEmpty();
    }

    @Test
    void IP_최근_1시간_요청이_20회이면_가장_오래된_유효_요청의_만료를_기다린다() {
        List<LocalDateTime> ipAttempts = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> now.minusMinutes(index))
                .toList();

        assertThat(policy.retryAt(List.of(), ipAttempts, now))
                .contains(now.minusMinutes(20).plusHours(1));
    }

    @Test
    void 여러_제한이_동시에_걸리면_모든_제한이_풀리는_가장_늦은_시각을_반환한다() {
        LocalDateTime emailAttempt = now.minusSeconds(30);
        List<LocalDateTime> ipAttempts = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> now.minusMinutes(index))
                .toList();

        assertThat(policy.retryAt(List.of(emailAttempt), ipAttempts, now))
                .contains(now.minusMinutes(20).plusHours(1));
    }

    @Test
    void 만료_경계에_도달한_요청은_제한하지_않는다() {
        assertThat(policy.retryAt(List.of(now.minusMinutes(2)), List.of(), now)).isEmpty();
    }
}
