package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record SalesReviewSummaryResponse(
        String summary,
        Sentiment sentiment,
        List<String> keywords
) {

    public record Sentiment(BigDecimal positive, BigDecimal neutral, BigDecimal negative) {}
}
