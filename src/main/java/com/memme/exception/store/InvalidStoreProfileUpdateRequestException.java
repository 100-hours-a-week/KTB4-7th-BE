package com.memme.exception.store;

import com.memme.dto.common.FieldError;
import java.util.List;

public class InvalidStoreProfileUpdateRequestException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public InvalidStoreProfileUpdateRequestException(List<FieldError> fieldErrors) {
        super("입력값을 확인해 주세요.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
