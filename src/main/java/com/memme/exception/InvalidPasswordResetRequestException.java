package com.memme.exception;

import com.memme.dto.common.FieldError;
import java.util.List;

public class InvalidPasswordResetRequestException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public InvalidPasswordResetRequestException(List<FieldError> fieldErrors) {
        super("입력값을 확인해 주세요.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
