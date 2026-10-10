package com.memme.dto.sales;

import java.time.OffsetDateTime;
import java.util.List;

public record SalesAnalysisMonthOptionsResponse(List<MonthOption> months) {
    public record MonthOption(String targetMonth, Long uploadId, String fileName, OffsetDateTime uploadedAt) {
    }
}
