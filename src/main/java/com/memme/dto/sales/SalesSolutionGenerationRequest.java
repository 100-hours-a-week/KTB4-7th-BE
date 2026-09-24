package com.memme.dto.sales;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SalesSolutionGenerationRequest(
        @NotNull @Positive Long storeId,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Positive Long salesAnalysisId,
        @NotNull LocalDate targetDate,
        @NotNull @Pattern(regexp = "UPLOAD|SCHEDULED") String triggerType,
        @NotNull @Valid SalesSolutionMetrics metrics
) {}
