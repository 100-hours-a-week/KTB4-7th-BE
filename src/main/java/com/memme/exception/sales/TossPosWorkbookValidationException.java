package com.memme.exception.sales;

public class TossPosWorkbookValidationException extends RuntimeException {

    public TossPosWorkbookValidationException(String message) {
        super(message);
    }

    public TossPosWorkbookValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
