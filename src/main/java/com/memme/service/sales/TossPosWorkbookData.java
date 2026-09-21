package com.memme.service.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record TossPosWorkbookData(
    LocalDate periodStart,
    LocalDate periodEnd,
    List<SalesOrder> orders,
    List<SalesOrderItem> items,
    List<DailySalesSummary> dailySummaries
) {

    public TossPosWorkbookData {
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        orders = List.copyOf(orders);
        items = List.copyOf(items);
        dailySummaries = List.copyOf(dailySummaries);
    }
}
