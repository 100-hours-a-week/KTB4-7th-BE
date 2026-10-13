package com.memme.service.sales.profit;

import com.memme.dto.sales.ProfitAiInsightRequest;
import com.memme.dto.sales.ProfitAiInsightResponse;

public interface ProfitAiInsightClient {
    ProfitAiInsightResponse generate(ProfitAiInsightRequest request);
}
