package com.memme.exception;

public class SalesSolutionAiException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final boolean retryable;

    public SalesSolutionAiException(int statusCode, String code, String message, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.retryable = retryable;
    }

    public SalesSolutionAiException(
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
