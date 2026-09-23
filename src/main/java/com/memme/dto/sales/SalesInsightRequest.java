package com.memme.dto.sales;

import java.time.YearMonth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SalesInsightRequest(
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long salesAnalysisId,
        @NotNull @Positive Long analysisRunId,
        @NotNull YearMonth targetMonth,
        @NotNull SalesInsightTriggerType triggerType,
        @NotNull @Valid SalesInsightMetrics metrics,
        @Min(1) @Max(3) int maxInsightCount
) {}
