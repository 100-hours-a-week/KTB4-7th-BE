package com.memme.exception;

import com.memme.dto.common.FieldError;
import java.util.List;

public class DuplicateSignupException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public DuplicateSignupException(List<FieldError> fieldErrors) {
        super("이미 가입된 이메일 또는 휴대폰 번호입니다.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
