package com.memme.service.sales.profit;

import java.time.LocalDate;
import java.util.Objects;

public record DailyProfitSales(LocalDate salesDate, long totalNetAmount) {

    public DailyProfitSales {
        Objects.requireNonNull(salesDate, "salesDate");
    }
}
