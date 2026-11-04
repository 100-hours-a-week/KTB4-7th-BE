package com.memme.dto.auth;

public record SignupRateLimitErrorResponse(
        String message,
        Void data,
        long retryAfterSeconds
) {
}
