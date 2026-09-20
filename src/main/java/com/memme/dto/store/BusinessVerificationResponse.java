package com.memme.dto.store;

import java.time.OffsetDateTime;

public record BusinessVerificationResponse(
        Long businessVerificationId,
        OffsetDateTime expiresAt
) {
}
