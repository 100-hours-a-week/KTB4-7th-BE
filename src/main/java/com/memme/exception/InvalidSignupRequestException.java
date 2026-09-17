package com.memme.exception;

public class InvalidSignupRequestException extends RuntimeException {

    public InvalidSignupRequestException() {
        super("입력값 또는 필수 약관 동의를 확인해 주세요.");
    }
}
