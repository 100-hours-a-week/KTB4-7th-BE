package com.memme.exception.sales;

public class SalesForecastAiException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final boolean retryable;

    public SalesForecastAiException(
            int statusCode,
            String code,
            String message,
            boolean retryable
    ) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.retryable = retryable;
    }

    public SalesForecastAiException(
            int statusCode,
            String code,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
