package com.memme.dto.sales;

import java.time.YearMonth;
import java.util.List;

public record SalesInsightResponse(
        YearMonth targetMonth,
        List<String> insights
) {}
