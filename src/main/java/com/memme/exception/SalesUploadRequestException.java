package com.memme.exception;

public class SalesUploadRequestException extends RuntimeException {

    private final String failReason;

    public SalesUploadRequestException(String failReason, String message) {
        super(message);
        this.failReason = failReason;
    }

    public String getFailReason() {
        return failReason;
    }
}
