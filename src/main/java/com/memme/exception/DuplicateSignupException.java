package com.memme.exception;

public class DuplicateSignupException extends RuntimeException {

    public DuplicateSignupException() {
        super("이미 가입된 이메일 또는 휴대폰 번호입니다.");
    }
}
