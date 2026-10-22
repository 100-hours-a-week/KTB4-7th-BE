package com.memme.dto.sales;

public record SalesUploadStatusResponse(
        String message,
        String status,
        String failReason,
        Data data
) {

    public record Data(
            Long uploadId,
            Long analysisRunId,
            String processingPhase,
            Long analysisId,
            String aiInsightStatus,
            boolean retryable
    ) {}
}
