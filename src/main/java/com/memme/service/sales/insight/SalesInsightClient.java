package com.memme.service.sales.insight;

import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightResponse;

public interface SalesInsightClient {

    SalesInsightResponse generate(SalesInsightRequest request);
}
