package com.memme.service.ranking;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ranking.recalculation")
public class RankingRecalculationLockProperties {

    private Provider lockProvider = Provider.MYSQL;
    private Duration lockWaitTimeout = Duration.ofSeconds(30);
    private Duration lockWatchdogTimeout = Duration.ofSeconds(30);
    private String lockKeyPrefix = "memme:ranking:recalculation:lock:v1";

    public Provider getLockProvider() {
        return lockProvider;
    }

    public void setLockProvider(Provider lockProvider) {
        this.lockProvider = Objects.requireNonNull(lockProvider, "lockProvider must not be null");
    }

    public Duration getLockWaitTimeout() {
        return lockWaitTimeout;
    }

    public void setLockWaitTimeout(Duration lockWaitTimeout) {
        this.lockWaitTimeout = positive(lockWaitTimeout, "lockWaitTimeout");
    }

    public Duration getLockWatchdogTimeout() {
        return lockWatchdogTimeout;
    }

    public void setLockWatchdogTimeout(Duration lockWatchdogTimeout) {
        this.lockWatchdogTimeout = positive(lockWatchdogTimeout, "lockWatchdogTimeout");
    }

    public String getLockKeyPrefix() {
        return lockKeyPrefix;
    }

    public void setLockKeyPrefix(String lockKeyPrefix) {
        if (lockKeyPrefix == null || lockKeyPrefix.isBlank()) {
            throw new IllegalArgumentException("lockKeyPrefix must not be blank");
        }
        this.lockKeyPrefix = lockKeyPrefix.trim();
    }

    private Duration positive(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be greater than zero");
        }
        try {
            if (value.toMillis() == 0) {
                throw new IllegalArgumentException(propertyName + " must be at least one millisecond");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(propertyName + " must fit in milliseconds", exception);
        }
        return value;
    }

    public enum Provider {
        MYSQL,
        REDIS
    }
}
