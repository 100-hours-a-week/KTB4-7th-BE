package com.memme.exception.auth;

public class LoginRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(long retryAfterSeconds) {
        super("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
