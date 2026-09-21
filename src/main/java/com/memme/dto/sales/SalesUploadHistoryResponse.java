package com.memme.dto.sales;

import java.time.OffsetDateTime;
import java.util.List;

public record SalesUploadHistoryResponse(
        Connection connection,
        List<Item> items,
        int page,
        int size,
        int totalPages,
        long totalCount
) {

    public record Connection(
            OffsetDateTime lastUploadedAt,
            long totalAppliedRecordCount,
            String latestStatus
    ) {}

    public record Item(
            Long uploadId,
            String fileName,
            OffsetDateTime uploadedAt,
            long recordCount,
            long appliedRecordCount,
            String status,
            String failReason
    ) {}
}
