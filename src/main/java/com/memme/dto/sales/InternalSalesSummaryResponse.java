package com.memme.dto.sales;

import java.math.BigDecimal;

public record InternalSalesSummaryResponse(
        SalesPeriod period,
        BigDecimal totalSales,
        long orderCount,
        BigDecimal averageOrderValue,
        BigDecimal changeRate
) {
}
