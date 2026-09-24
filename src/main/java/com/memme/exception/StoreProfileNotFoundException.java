package com.memme.exception;

public class StoreProfileNotFoundException extends RuntimeException {

    public StoreProfileNotFoundException() {
        super("매장 정보를 찾을 수 없습니다.");
    }
}
