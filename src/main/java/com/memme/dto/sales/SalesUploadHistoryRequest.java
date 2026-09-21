package com.memme.dto.sales;

import java.time.YearMonth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

public record SalesUploadHistoryRequest(
        @DateTimeFormat(pattern = "yyyy-MM") YearMonth targetMonth,
        @Min(1) @Max(5) Integer page,
        @Min(10) @Max(10) Integer size
) {

    public SalesUploadHistoryRequest {
        page = page == null ? 1 : page;
        size = size == null ? 10 : size;
    }
}
