package com.memme.dto.sales;

import java.util.List;

public record StoreCostItemValidationErrors(List<StoreCostItemFieldError> fieldErrors) {
    public record StoreCostItemFieldError(String field, String code, String message) {
    }
}
