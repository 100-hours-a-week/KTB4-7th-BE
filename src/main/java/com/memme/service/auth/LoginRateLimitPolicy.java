package com.memme.service.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimitPolicy {

    public static final int ACCOUNT_WINDOW_MINUTES = 15;
    public static final int IP_WINDOW_MINUTES = 10;
    public static final int IP_FAILURE_LIMIT = 30;
    public static final int ACCOUNT_ATTEMPT_LOOKUP_LIMIT = 20;
    public static final int IP_ATTEMPT_LOOKUP_LIMIT = IP_FAILURE_LIMIT;

    public Optional<Duration> cooldownAfterFailureCount(int failureCount) {
        if (failureCount < 5) {
            return Optional.empty();
        }
        if (failureCount < 10) {
            return Optional.of(Duration.ofSeconds(30));
        }
        if (failureCount < 15) {
            return Optional.of(Duration.ofMinutes(1));
        }
        if (failureCount < 20) {
            return Optional.of(Duration.ofMinutes(5));
        }
        return Optional.of(Duration.ofMinutes(15));
    }

    public Optional<LocalDateTime> retryAt(
            List<AccountFailure> accountFailures,
            List<LocalDateTime> ipFailures,
            LocalDateTime now
    ) {
        LocalDateTime accountRetryAt = accountFailures.stream()
                .map(AccountFailure::blockedUntil)
                .filter(blockedUntil -> blockedUntil != null && blockedUntil.isAfter(now))
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime ipRetryAt = null;
        if (ipFailures.size() >= IP_FAILURE_LIMIT) {
            ipRetryAt = ipFailures.stream()
                    .min(Comparator.naturalOrder())
                    .orElseThrow()
                    .plusMinutes(IP_WINDOW_MINUTES);
            if (!ipRetryAt.isAfter(now)) {
                ipRetryAt = null;
            }
        }

        if (accountRetryAt == null) {
            return Optional.ofNullable(ipRetryAt);
        }
        if (ipRetryAt == null || accountRetryAt.isAfter(ipRetryAt)) {
            return Optional.of(accountRetryAt);
        }
        return Optional.of(ipRetryAt);
    }

    public record AccountFailure(LocalDateTime failedAt, LocalDateTime blockedUntil) {
    }
}
