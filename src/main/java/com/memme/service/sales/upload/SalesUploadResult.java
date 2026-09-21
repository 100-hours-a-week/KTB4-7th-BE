package com.memme.service.sales.upload;

import java.time.LocalDate;

public record SalesUploadResult(
        Long uploadId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalRecordCount,
        int appliedRecordCount
) {
}
