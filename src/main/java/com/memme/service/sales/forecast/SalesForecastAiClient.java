package com.memme.service.sales.forecast;

import com.memme.dto.sales.SalesForecastBatchRequest;
import com.memme.dto.sales.SalesForecastBatchResponse;

public interface SalesForecastAiClient {

    SalesForecastBatchResponse createForecast(SalesForecastBatchRequest request);
}
