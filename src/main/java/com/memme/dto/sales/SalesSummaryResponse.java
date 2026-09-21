package com.memme.dto.sales;

import java.math.BigDecimal;

public record SalesSummaryResponse(
        SalesPeriod period,
        BigDecimal netSales,
        long orderCount,
        BigDecimal averageOrderValue,
        BigDecimal changeRate
) {}
