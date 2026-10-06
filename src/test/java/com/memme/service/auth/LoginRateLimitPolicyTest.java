package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginRateLimitPolicyTest {

    private final LoginRateLimitPolicy policy = new LoginRateLimitPolicy();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);

    @Test
    void 계정_실패_횟수에_따라_단계별_쿨다운을_반환한다() {
        assertThat(policy.cooldownAfterFailureCount(4)).isEmpty();
        assertThat(policy.cooldownAfterFailureCount(5)).contains(Duration.ofSeconds(30));
        assertThat(policy.cooldownAfterFailureCount(9)).contains(Duration.ofSeconds(30));
        assertThat(policy.cooldownAfterFailureCount(10)).contains(Duration.ofMinutes(1));
        assertThat(policy.cooldownAfterFailureCount(14)).contains(Duration.ofMinutes(1));
        assertThat(policy.cooldownAfterFailureCount(15)).contains(Duration.ofMinutes(5));
        assertThat(policy.cooldownAfterFailureCount(19)).contains(Duration.ofMinutes(5));
        assertThat(policy.cooldownAfterFailureCount(20)).contains(Duration.ofMinutes(15));
    }

    @Test
    void 이메일과_IP_제한이_동시에_걸리면_더_늦게_해제되는_시각을_반환한다() {
        List<LoginRateLimitPolicy.AccountFailure> accountFailures = List.of(
                new LoginRateLimitPolicy.AccountFailure(now.minusSeconds(1), now.plusSeconds(30))
        );
        List<LocalDateTime> ipFailures = java.util.stream.IntStream.range(0, 30)
                .mapToObj(index -> now.minusSeconds(index + 1L))
                .toList();

        assertThat(policy.retryAt(accountFailures, ipFailures, now))
                .contains(now.minusSeconds(30).plusMinutes(10));
    }

    @Test
    void 계정_실패가_5회_미만이고_IP가_30회_미만이면_제한하지_않는다() {
        List<LoginRateLimitPolicy.AccountFailure> accountFailures = List.of(
                new LoginRateLimitPolicy.AccountFailure(now.minusMinutes(1), null)
        );
        List<LocalDateTime> ipFailures = java.util.stream.IntStream.range(0, 29)
                .mapToObj(index -> now.minusSeconds(index + 1L))
                .toList();

        assertThat(policy.retryAt(accountFailures, ipFailures, now)).isEmpty();
    }
}
