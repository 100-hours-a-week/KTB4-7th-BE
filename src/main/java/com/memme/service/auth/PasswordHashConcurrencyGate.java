package com.memme.service.auth;

import com.memme.exception.auth.PasswordHashCapacityExceededException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHashConcurrencyGate {

    private final PasswordEncoder passwordEncoder;
    private final Semaphore permits;
    private final long waitTimeoutMillis;

    public PasswordHashConcurrencyGate(
            PasswordEncoder passwordEncoder,
            @Value("${app.password-hash.max-concurrency}") int maxConcurrency,
            @Value("${app.password-hash.wait-timeout-ms}") long waitTimeoutMillis
    ) {
        if (maxConcurrency < 1 || waitTimeoutMillis < 0) {
            throw new IllegalArgumentException("Password hash concurrency settings must be non-negative and have at least one permit");
        }
        this.passwordEncoder = passwordEncoder;
        this.permits = new Semaphore(maxConcurrency, true);
        this.waitTimeoutMillis = waitTimeoutMillis;
    }

    public String encode(CharSequence rawPassword) {
        return call(() -> passwordEncoder.encode(rawPassword));
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return call(() -> passwordEncoder.matches(rawPassword, encodedPassword));
    }

    private <T> T call(Supplier<T> operation) {
        try {
            if (!permits.tryAcquire(waitTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new PasswordHashCapacityExceededException();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PasswordHashCapacityExceededException(exception);
        }
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }
}
