package com.memme.service.auth;

import com.memme.exception.auth.PasswordResetRateLimitExceededException;
import com.memme.repository.auth.PasswordResetRateLimitRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetRateLimiter {

    private final PasswordResetRateLimitRepository rateLimitRepository;
    private final PasswordResetRateLimitKeyHasher keyHasher;
    private final Clock clock;

    public PasswordResetRateLimiter(
            PasswordResetRateLimitRepository rateLimitRepository,
            PasswordResetRateLimitKeyHasher keyHasher,
            Clock clock
    ) {
        this.rateLimitRepository = rateLimitRepository;
        this.keyHasher = keyHasher;
        this.clock = clock;
    }

    public void recordIfAllowed(String email, String ipAddress) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<LocalDateTime> retryAt = rateLimitRepository.recordIfAllowed(
                keyHasher.hashEmail(email),
                keyHasher.hashIp(ipAddress),
                now
        );
        retryAt.ifPresent(allowedAt -> {
            Duration retryDuration = Duration.between(now, allowedAt);
            long retryAfterSeconds = Math.max(
                    1,
                    retryDuration.getSeconds() + (retryDuration.getNano() == 0 ? 0 : 1)
            );
            throw new PasswordResetRateLimitExceededException(retryAfterSeconds);
        });
    }
}
