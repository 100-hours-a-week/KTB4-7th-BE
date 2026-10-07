package com.memme.dto.store;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StoreCostItemResponse(
        String costMonth,
        long rentAmount,
        long laborAmount,
        BigDecimal ingredientCostRate,
        OffsetDateTime updatedAt
) {
}
