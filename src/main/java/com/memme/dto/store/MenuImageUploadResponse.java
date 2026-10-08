package com.memme.dto.store;

import java.time.OffsetDateTime;

public record MenuImageUploadResponse(
        long batchId,
        int imageCount,
        OffsetDateTime uploadedAt,
        String next
) {
}
