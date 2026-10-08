package com.memme.dto.store;

import java.time.OffsetDateTime;
import java.util.List;

public record MenuConfirmationResponse(
        long batchId,
        long menuRevision,
        OffsetDateTime savedAt,
        List<Item> items
) {
    public record Item(
            long itemId,
            long menuId,
            String name,
            long price,
            String category
    ) {
    }
}
