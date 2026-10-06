package com.memme.service.auth;

import com.memme.exception.auth.LoginRateLimitExceededException;
import com.memme.repository.auth.LoginRateLimitRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimiter {

    private final LoginRateLimitRepository repository;
    private final LoginRateLimitKeyHasher keyHasher;
    private final Clock clock;

    public LoginRateLimiter(LoginRateLimitRepository repository, LoginRateLimitKeyHasher keyHasher, Clock clock) {
        this.repository = repository;
        this.keyHasher = keyHasher;
        this.clock = clock;
    }

    public void checkAllowed(String email, String ipAddress) {
        LocalDateTime now = now();
        check(repository.checkAllowed(hashEmail(email), hashIp(ipAddress), now), now);
    }

    public void recordFailure(String email, String ipAddress) {
        LocalDateTime now = now();
        check(repository.recordFailureIfAllowed(hashEmail(email), hashIp(ipAddress), now), now);
    }

    public void clearAccountFailures(String email, String ipAddress) {
        LocalDateTime now = now();
        check(repository.clearAccountFailuresIfAllowed(hashEmail(email), hashIp(ipAddress), now), now);
    }

    private String hashEmail(String email) {
        return keyHasher.hashEmail(email);
    }

    private String hashIp(String ipAddress) {
        return keyHasher.hashIp(ipAddress);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void check(Optional<LocalDateTime> retryAt, LocalDateTime now) {
        retryAt.ifPresent(allowedAt -> {
            Duration retryDuration = Duration.between(now, allowedAt);
            long retryAfterSeconds = Math.max(
                    1,
                    retryDuration.getSeconds() + (retryDuration.getNano() == 0 ? 0 : 1)
            );
            throw new LoginRateLimitExceededException(retryAfterSeconds);
        });
    }
}
