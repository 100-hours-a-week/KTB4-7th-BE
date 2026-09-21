package com.memme.dto.sales;

import java.time.LocalDate;

public record SalesPeriod(
        String type,
        LocalDate startDate,
        LocalDate endDate
) {}
