package com.memme.exception.auth;

public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("로그인이 필요합니다.");
    }
}
