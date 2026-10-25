package com.memme.service.sales.outbox;

import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

@Component
public class SalesOutboxRetryPolicy {
    private static final long[] DELAYS = {10, 30, 120, 300};
    public boolean retryable(Throwable failure) {
        // 확정된 영구 오류는 포장된 외부 예외보다 우선한다.
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof FileNotFoundException || cause instanceof NoSuchFileException) return false;
            if (cause instanceof AwsServiceException aws && aws.statusCode() == 404) return false;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException) return true;
            if (cause instanceof AwsServiceException aws && (aws.statusCode() == 429 || aws.statusCode() >= 500)) return true;
            if (cause instanceof SdkClientException && cause.getCause() instanceof IOException) return true;
            if (cause instanceof java.sql.SQLTransientException || cause instanceof java.sql.SQLRecoverableException) return true;
        }
        return false;
    }
    public Duration delay(int attempt) {
        long base = DELAYS[Math.max(0, Math.min(attempt - 1, DELAYS.length - 1))] * 1000;
        return Duration.ofMillis(Math.round(base * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
    }
}
