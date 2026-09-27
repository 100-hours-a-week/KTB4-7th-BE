package com.memme.dto.sales;

import java.util.List;

public record SalesAvailableMonthsResponse(
        List<String> months
) {}
