package com.memme.dto.auth;

import java.time.OffsetDateTime;

public record BusinessVerificationResponse(
        Long businessVerificationId,
        OffsetDateTime expiresAt
) {
}
