package com.memme.exception;

public class InvalidBusinessNumberException extends RuntimeException {

    public InvalidBusinessNumberException() {
        super("invalid_business_number");
    }
}
