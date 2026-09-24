package com.memme.dto.sales;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesSolutionGenerationResponse(
        String message,
        SalesSolutionGenerationStatus status,
        @Valid Data data,
        @Valid Error error
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            LocalDate targetDate,
            @Valid List<SolutionCard> solutionCards,
            String modelVersion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SolutionCard(
            @Min(1) @Max(3) int rankNo,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String summaryText,
            @NotBlank @Size(max = 1_000) String detailText,
            String evidence
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, Boolean retryable) {}
}
