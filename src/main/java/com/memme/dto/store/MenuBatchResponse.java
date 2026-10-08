package com.memme.dto.store;

import java.time.OffsetDateTime;
import java.util.List;

public record MenuBatchResponse(
        long batchId,
        long baseMenuRevision,
        OffsetDateTime uploadedAt,
        OffsetDateTime savedAt,
        String failReason,
        List<DetectedItem> detectedItems
) {
    public record DetectedItem(
            long itemId,
            int order,
            String name,
            Long price,
            String category,
            boolean priceReviewRequired
    ) {
    }
}
