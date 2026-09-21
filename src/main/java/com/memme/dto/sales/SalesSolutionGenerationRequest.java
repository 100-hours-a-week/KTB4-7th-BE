package com.memme.dto.sales;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SalesSolutionGenerationRequest(
        Long storeId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long salesAnalysisId,
        LocalDate targetDate,
        String triggerType,
        Map<String, Object> metrics
) {}
