package com.memme.service.sales.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import static org.assertj.core.api.Assertions.assertThat;

class SalesOutboxRetryPolicyTest {
    private final SalesOutboxRetryPolicy policy = new SalesOutboxRetryPolicy();
    @Test void retryKnownTemporaryDatabaseErrorsButNotUnknownFailures() {
        assertThat(policy.retryable(new RuntimeException(new TransientDataAccessResourceException("temporary")))).isTrue();
        assertThat(policy.retryable(new java.sql.SQLNonTransientConnectionException("connection lost", "08S01"))).isTrue();
        assertThat(policy.retryable(new IllegalStateException("unknown"))).isFalse();
    }
    @Test void missingFileIsPermanentEvenWhenWrapped() {
        assertThat(policy.retryable(new java.io.UncheckedIOException(new java.nio.file.NoSuchFileException("missing")))).isFalse();
    }
    @Test void backoffRemainsInsideApprovedJitterBounds() {
        long[] seconds = {10, 30, 120, 300};
        for (int attempt = 1; attempt <= 4; attempt++) {
            long value = policy.delay(attempt).toMillis();
            assertThat(value).isBetween(seconds[attempt - 1] * 800, seconds[attempt - 1] * 1200);
        }
    }
}
