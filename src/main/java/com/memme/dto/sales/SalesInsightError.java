package com.memme.dto.sales;

import jakarta.validation.constraints.NotBlank;

public record SalesInsightError(
        @NotBlank String code,
        boolean retryable
) {}
