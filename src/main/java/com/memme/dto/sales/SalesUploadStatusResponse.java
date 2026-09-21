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
            Progress progress,
            Long analysisId,
            boolean retryable
    ) {}

    public record Progress(String step, int percent) {}
}
