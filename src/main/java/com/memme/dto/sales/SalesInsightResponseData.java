package com.memme.dto.sales;

import java.time.YearMonth;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesInsightResponseData(
        YearMonth targetMonth,
        @Size(min = 1, max = 3) List<@NotBlank @Size(max = 100) String> insights,
        List<@NotBlank String> missingData
) {
    public SalesInsightResponseData {
        insights = copyOfNullable(insights);
        missingData = copyOfNullable(missingData);
    }

    private static <T> List<T> copyOfNullable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
