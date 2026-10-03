package com.memme.service.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimitPolicy {

    public static final int EMAIL_COOLDOWN_SECONDS = 2 * 60;
    public static final int EMAIL_DAILY_LIMIT = 5;
    public static final int IP_HOURLY_LIMIT = 20;

    public Optional<LocalDateTime> retryAt(
            List<LocalDateTime> recentEmailAttempts,
            List<LocalDateTime> recentIpAttempts,
            LocalDateTime now
    ) {
        List<LocalDateTime> emailAttempts = newestFirst(recentEmailAttempts);
        List<LocalDateTime> ipAttempts = newestFirst(recentIpAttempts);
        LocalDateTime latestAllowedAt = null;

        if (!emailAttempts.isEmpty()) {
            LocalDateTime cooldownAt = emailAttempts.getFirst().plusSeconds(EMAIL_COOLDOWN_SECONDS);
            if (cooldownAt.isAfter(now)) {
                latestAllowedAt = cooldownAt;
            }
        }

        if (emailAttempts.size() >= EMAIL_DAILY_LIMIT) {
            LocalDateTime dailyLimitAt = emailAttempts.get(EMAIL_DAILY_LIMIT - 1).plusHours(24);
            latestAllowedAt = laterOf(latestAllowedAt, dailyLimitAt, now);
        }

        if (ipAttempts.size() >= IP_HOURLY_LIMIT) {
            LocalDateTime hourlyLimitAt = ipAttempts.get(IP_HOURLY_LIMIT - 1).plusHours(1);
            latestAllowedAt = laterOf(latestAllowedAt, hourlyLimitAt, now);
        }

        return Optional.ofNullable(latestAllowedAt);
    }

    private List<LocalDateTime> newestFirst(List<LocalDateTime> attempts) {
        List<LocalDateTime> sorted = new ArrayList<>(attempts);
        sorted.sort(Comparator.reverseOrder());
        return sorted;
    }

    private LocalDateTime laterOf(LocalDateTime current, LocalDateTime candidate, LocalDateTime now) {
        if (!candidate.isAfter(now)) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
