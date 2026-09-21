package com.memme.dto.sales;

import java.time.YearMonth;

public record SalesForecastBatchRequest(
        Long storeId,
        Long uploadId,
        String dailySales,
        YearMonth targetMonth
) {}
