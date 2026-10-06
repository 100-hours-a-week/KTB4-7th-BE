package com.memme.service.auth;

import com.memme.repository.auth.LoginRateLimitRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimitCleanupJob {

    private final LoginRateLimitRepository repository;
    private final Clock clock;

    public LoginRateLimitCleanupJob(LoginRateLimitRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "${LOGIN_RATE_LIMIT_CLEANUP_CRON:0 */5 * * * *}")
    public void deleteExpiredAttempts() {
        repository.deleteExpiredBefore(
                LocalDateTime.now(clock).minusMinutes(LoginRateLimitPolicy.ACCOUNT_WINDOW_MINUTES)
        );
    }
}
