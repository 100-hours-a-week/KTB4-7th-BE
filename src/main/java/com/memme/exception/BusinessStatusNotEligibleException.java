package com.memme.exception;

public class BusinessStatusNotEligibleException extends RuntimeException {

    public BusinessStatusNotEligibleException() {
        super("business_status_not_eligible");
    }
}
