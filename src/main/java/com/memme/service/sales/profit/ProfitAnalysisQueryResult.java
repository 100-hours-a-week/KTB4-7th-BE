package com.memme.service.sales.profit;

import com.memme.dto.sales.ProfitAnalysisResponse;
import com.memme.dto.sales.ProfitMissingCostsResponse;

public sealed interface ProfitAnalysisQueryResult {
    record Completed(ProfitAnalysisResponse data) implements ProfitAnalysisQueryResult {
    }

    record CostInputRequired(ProfitMissingCostsResponse data) implements ProfitAnalysisQueryResult {
    }

    record Empty() implements ProfitAnalysisQueryResult {
    }
}
