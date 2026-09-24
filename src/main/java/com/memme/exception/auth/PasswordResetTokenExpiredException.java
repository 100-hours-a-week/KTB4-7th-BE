package com.memme.exception.auth;

public class PasswordResetTokenExpiredException extends RuntimeException {

    public PasswordResetTokenExpiredException() {
        super("비밀번호 재설정 링크가 만료되었거나 이미 사용되었습니다.");
    }
}
