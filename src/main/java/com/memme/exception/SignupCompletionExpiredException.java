package com.memme.exception;

public class SignupCompletionExpiredException extends RuntimeException {

    public enum Reason {
        SIGNUP_DRAFT("회원가입 임시 정보가 만료되었습니다. 다시 가입해 주세요."),
        BUSINESS_VERIFICATION("사업자 인증 결과가 만료되었습니다. 다시 인증해 주세요.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    public SignupCompletionExpiredException(Reason reason) {
        super(reason.message);
    }
}
