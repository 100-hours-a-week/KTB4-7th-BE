package com.memme.dto.sales;

import java.util.List;

public record SalesUploadValidationErrorResponse(
        String message,
        String failReason,
        ValidationDetails details,
        Object data
) {

    public record ValidationDetails(List<String> missingColumns) {}
}
