package com.memme.dto.sales;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SalesSolutionGenerationResponse(
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate targetDate,
        List<SolutionCard> solutionCards,
        String modelVersion,
        String promptVersion
) {

    public record SolutionCard(int rankNo, String title, String summaryText, String detailText) {}
}
