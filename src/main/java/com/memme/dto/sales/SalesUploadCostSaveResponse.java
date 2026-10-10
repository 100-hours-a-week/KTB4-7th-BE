package com.memme.dto.sales;

import java.util.List;

public record SalesUploadCostSaveResponse(Long uploadId, List<String> costMonths, String next) {
    public SalesUploadCostSaveResponse(Long uploadId, List<String> costMonths) {
        this(uploadId, costMonths, "PROFIT_ANALYSIS");
    }
}
