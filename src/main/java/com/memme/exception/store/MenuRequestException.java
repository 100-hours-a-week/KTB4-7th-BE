package com.memme.exception.store;

import org.springframework.http.HttpStatus;

public class MenuRequestException extends RuntimeException {

    private final HttpStatus status;
    private final Object data;

    public MenuRequestException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public MenuRequestException(HttpStatus status, String message, Object data) {
        super(message);
        this.status = status;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getData() {
        return data;
    }
}
