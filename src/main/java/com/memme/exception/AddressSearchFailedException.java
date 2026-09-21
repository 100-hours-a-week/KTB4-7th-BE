package com.memme.exception;

public class AddressSearchFailedException extends RuntimeException {

    public AddressSearchFailedException() {
        super("주소 검색 서비스를 이용할 수 없습니다. 다시 시도해주세요.");
    }
}
