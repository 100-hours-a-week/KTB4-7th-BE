package com.memme.exception;

public class AddressSearchFailedException extends RuntimeException {

    public AddressSearchFailedException() {
        super("address_search_failed");
    }
}
