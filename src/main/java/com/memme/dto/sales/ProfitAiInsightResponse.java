package com.memme.dto.sales;

import java.util.List;

public record ProfitAiInsightResponse(String status, Data data) {
    public record Data(List<String> insights) {
    }
}
