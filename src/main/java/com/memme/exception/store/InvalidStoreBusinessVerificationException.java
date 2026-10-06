package com.memme.exception.store;

import com.memme.dto.common.FieldError;
import java.util.List;

public class InvalidStoreBusinessVerificationException extends RuntimeException {

    public InvalidStoreBusinessVerificationException() {
        super("입력값을 확인해 주세요.");
    }

    public List<FieldError> getFieldErrors() {
        return List.of(new FieldError("businessVerificationId", "사업자 인증 결과가 유효하지 않습니다."));
    }
}
