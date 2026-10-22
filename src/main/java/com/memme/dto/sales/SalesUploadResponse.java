package com.memme.dto.sales;

public record SalesUploadResponse(
        Long uploadId,
        Long analysisRunId,
        String processingPhase
) {
    public SalesUploadResponse(Long uploadId, Long analysisRunId) {
        this(uploadId, analysisRunId, null);
    }
}
