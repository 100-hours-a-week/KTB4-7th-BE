package com.memme.dto.sales;

import java.util.List;

public record SalesUploadCostValidationErrors(List<FieldError> fieldErrors) {
    public record FieldError(String costMonth, String field, String code, String message) {
    }
}
