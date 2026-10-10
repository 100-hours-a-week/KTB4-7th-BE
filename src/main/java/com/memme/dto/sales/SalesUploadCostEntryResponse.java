package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record SalesUploadCostEntryResponse(Long uploadId, List<Month> months) {
    public record Month(String costMonth, Long rentAmount, Long laborAmount,
                        BigDecimal ingredientCostRate, String source) {
    }
}
