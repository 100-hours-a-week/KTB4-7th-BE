package com.memme.service.sales;

import java.time.LocalDate;
import java.util.Objects;

public record DailySalesSummary(
    LocalDate salesDate,
    long totalNetAmount,
    long menuNetAmount,
    int orderCount,
    int menuQuantity
) {

    public DailySalesSummary {
        Objects.requireNonNull(salesDate, "salesDate");
    }
}
