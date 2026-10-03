package com.memme.service.auth;

import com.memme.repository.auth.PasswordResetRateLimitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimitCleanupJob {

    private final PasswordResetRateLimitRepository rateLimitRepository;
    private final Clock clock;

    public PasswordResetRateLimitCleanupJob(
            PasswordResetRateLimitRepository rateLimitRepository,
            Clock clock
    ) {
        this.rateLimitRepository = rateLimitRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${PASSWORD_RESET_RATE_LIMIT_CLEANUP_CRON:0 */5 * * * *}")
    public void deleteExpiredAttempts() {
        rateLimitRepository.deleteExpiredBefore(LocalDateTime.now(clock).minusHours(24));
    }
}
