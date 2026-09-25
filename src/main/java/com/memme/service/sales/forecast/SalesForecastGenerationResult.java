package com.memme.service.sales.forecast;

public record SalesForecastGenerationResult(Status status, boolean retryable) {

    public enum Status {
        COMPLETED,
        INSUFFICIENT_HISTORY,
        FAILED
    }
}
