package com.memme.dto.sales;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesInsightResponse(
        String message,
        @NotNull SalesInsightStatus status,
        @Valid SalesInsightResponseData data,
        @Valid SalesInsightError error
) {}
