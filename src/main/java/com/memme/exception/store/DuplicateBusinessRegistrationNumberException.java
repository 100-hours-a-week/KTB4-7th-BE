package com.memme.exception.store;

import com.memme.dto.common.FieldError;
import java.util.List;

public class DuplicateBusinessRegistrationNumberException extends RuntimeException {

    public DuplicateBusinessRegistrationNumberException() {
        super("이미 등록된 사업자등록번호입니다.");
    }

    public List<FieldError> getFieldErrors() {
        return List.of(new FieldError("businessRegNumber", "이미 등록된 사업자등록번호입니다."));
    }
}
