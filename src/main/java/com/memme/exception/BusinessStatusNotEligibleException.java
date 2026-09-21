package com.memme.exception;

import com.memme.dto.common.FieldError;
import java.util.List;

public class BusinessStatusNotEligibleException extends RuntimeException {

    public BusinessStatusNotEligibleException() {
        super("입력값을 확인해 주세요.");
    }

    public List<FieldError> getFieldErrors() {
        return List.of(new FieldError("businessRegNumber", "사업자등록번호를 확인해 주세요."));
    }
}
