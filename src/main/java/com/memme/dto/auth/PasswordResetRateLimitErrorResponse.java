package com.memme.dto.auth;

public record PasswordResetRateLimitErrorResponse(
        String message,
        Void data,
        long retryAfterSeconds
) {
}
