package com.memme.dto.auth;

public record LoginRateLimitErrorResponse(
        String message,
        Void data,
        long retryAfterSeconds
) {
}
