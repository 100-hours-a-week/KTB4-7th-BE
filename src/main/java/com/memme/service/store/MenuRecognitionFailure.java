package com.memme.service.store;

public class MenuRecognitionFailure extends RuntimeException {

    private final String reason;

    public MenuRecognitionFailure(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
