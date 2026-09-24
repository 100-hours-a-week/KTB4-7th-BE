package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationResponse;

public interface SalesSolutionAiClient {

    SalesSolutionGenerationResponse generate(SalesSolutionGenerationRequest request);
}
