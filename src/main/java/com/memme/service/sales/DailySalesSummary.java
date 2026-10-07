package com.memme.service.sales;

import java.time.LocalDate;
import java.util.Objects;

import com.memme.entity.sales.SalesDailyStatus;

public record DailySalesSummary(
    LocalDate salesDate,
    long totalNetAmount,
    long menuNetAmount,
    int orderCount,
    int menuQuantity,
    SalesDailyStatus status
) {

    public DailySalesSummary {
        Objects.requireNonNull(salesDate, "salesDate");
        Objects.requireNonNull(status, "status");
    }
}
