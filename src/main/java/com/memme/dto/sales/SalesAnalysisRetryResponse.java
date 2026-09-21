package com.memme.dto.sales;

public record SalesAnalysisRetryResponse(
        Long uploadId,
        Long analysisRunId,
        Long analysisId
) {}
