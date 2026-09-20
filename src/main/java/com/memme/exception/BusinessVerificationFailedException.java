package com.memme.exception;

public class BusinessVerificationFailedException extends RuntimeException {

    public BusinessVerificationFailedException() {
        super("business_verification_failed");
    }
}
