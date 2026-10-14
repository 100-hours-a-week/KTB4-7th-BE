package com.memme.exception.solution;

public class ExternalDataLookupException extends RuntimeException {

    private final String code;

    public ExternalDataLookupException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
