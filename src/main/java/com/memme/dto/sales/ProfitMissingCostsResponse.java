package com.memme.dto.sales;

import java.util.List;

public record ProfitMissingCostsResponse(List<String> missingCostMonths) {
    public ProfitMissingCostsResponse {
        missingCostMonths = List.copyOf(missingCostMonths);
    }
}
