package com.memme.exception.auth;

public class PasswordHashCapacityExceededException extends RuntimeException {

    public PasswordHashCapacityExceededException() {
        super("Password hash capacity exceeded");
    }

    public PasswordHashCapacityExceededException(InterruptedException cause) {
        super("Password hash permit wait interrupted", cause);
    }
}
