package com.memme.service.sales.insight;

import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightResponse;

public interface SalesInsightClient {

    SalesInsightResponse generate(SalesInsightRequest request);

    default SalesInsightResponse generate(SalesInsightRequest request, String idempotencyKey) {
        return generate(request);
    }
}
