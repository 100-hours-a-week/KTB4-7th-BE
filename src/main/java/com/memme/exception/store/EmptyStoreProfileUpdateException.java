package com.memme.exception.store;

public class EmptyStoreProfileUpdateException extends RuntimeException {

    public EmptyStoreProfileUpdateException() {
        super("변경할 매장 정보가 없습니다.");
    }
}
