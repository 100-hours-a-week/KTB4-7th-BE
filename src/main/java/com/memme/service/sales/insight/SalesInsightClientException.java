package com.memme.service.sales.insight;

public class SalesInsightClientException extends RuntimeException {

    public SalesInsightClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SalesInsightClientException(String message) {
        super(message);
    }
}
