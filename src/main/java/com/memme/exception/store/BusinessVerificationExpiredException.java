package com.memme.exception.store;

public class BusinessVerificationExpiredException extends RuntimeException {

    public BusinessVerificationExpiredException() {
        super("사업자 인증 결과가 만료되었습니다.");
    }
}
