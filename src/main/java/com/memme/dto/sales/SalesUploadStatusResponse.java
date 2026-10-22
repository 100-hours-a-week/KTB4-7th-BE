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
    ) {
        public Data(Long uploadId, Long analysisRunId, Progress progress, Long analysisId, boolean retryable) {
            this(uploadId, analysisRunId,
                    progress == null || "PENDING".equals(progress.step()) || "COMPLETED".equals(progress.step())
                            ? null : progress.step(),
                    analysisId, "PENDING", retryable);
        }
    }

    public record Progress(String step, int percent) {}
}
