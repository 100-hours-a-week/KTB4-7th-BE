package com.memme.service.store.recognition;

public class MenuRecognitionAiException extends RuntimeException {

    private final String failReason;

    public MenuRecognitionAiException(String failReason, String message) {
        super(message);
        this.failReason = failReason;
    }

    public MenuRecognitionAiException(String failReason, String message, Throwable cause) {
        super(message, cause);
        this.failReason = failReason;
    }

    public String getFailReason() {
        return failReason;
    }
}
