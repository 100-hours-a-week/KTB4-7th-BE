package com.memme.dto.store;

import java.time.OffsetDateTime;
import java.util.List;

public record MenuBatchResponse(
        long batchId,
        long baseMenuRevision,
        OffsetDateTime uploadedAt,
        OffsetDateTime savedAt,
        String failReason,
        List<DetectedItem> detectedItems,
        List<FailedImage> failedImages
) {
    public MenuBatchResponse(long batchId, long baseMenuRevision, OffsetDateTime uploadedAt,
            OffsetDateTime savedAt, String failReason, List<DetectedItem> detectedItems) {
        this(batchId, baseMenuRevision, uploadedAt, savedAt, failReason, detectedItems, List.of());
    }

    public record FailedImage(long imageId, int order, String failReason) {
    }

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
