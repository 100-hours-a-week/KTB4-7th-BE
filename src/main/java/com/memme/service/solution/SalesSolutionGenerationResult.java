package com.memme.service.solution;

public record SalesSolutionGenerationResult(
        Status status,
        Long solutionBundleId,
        boolean retryable
) {

    public enum Status {
        COMPLETED,
        GENERATING,
        FORECAST_PENDING,
        INSUFFICIENT_HISTORY,
        EMPTY,
        FAILED
    }

    public static SalesSolutionGenerationResult of(Status status, Long bundleId) {
        return new SalesSolutionGenerationResult(status, bundleId, false);
    }
}
