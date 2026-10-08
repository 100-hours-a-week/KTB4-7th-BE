package com.memme.dto.store;

import java.time.OffsetDateTime;
import java.util.List;

public record MenuListResponse(
        long menuRevision,
        List<Item> items,
        DraftBatch draftBatch
) {
    public record Item(
            long id,
            int order,
            String name,
            long price,
            String category,
            OffsetDateTime updatedAt
    ) {
    }

    public record DraftBatch(
            long batchId,
            String status,
            OffsetDateTime uploadedAt,
            String failReason,
            OffsetDateTime savedAt
    ) {
    }
}
