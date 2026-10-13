package com.memme.service.sales.profit;

import com.memme.dto.sales.ProfitAiInsightRequest;
import com.memme.dto.sales.ProfitAiInsightResponse;
import com.memme.dto.sales.ProfitAnalysisResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfitInsightService {

    private final ProfitAiInsightClient client;

    public ProfitInsightService(ProfitAiInsightClient client) {
        this.client = client;
    }

    public ProfitAnalysisResponse.AiInsight describe(ProfitAiInsightRequest request) {
        ProfitAiInsightResponse response = client.generate(request);
        return new ProfitAnalysisResponse.AiInsight(response.status(),
                List.copyOf(response.data().insights()));
    }
}
