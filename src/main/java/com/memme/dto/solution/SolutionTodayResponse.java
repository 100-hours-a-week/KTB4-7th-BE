package com.memme.dto.solution;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SolutionTodayResponse(
        String message,
        String status,
        Data data
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(
            String storeName,
            String screenTitle,
            Long solutionBundleId,
            LocalDate targetDate,
            List<SolutionCardResponse> solutionCards
    ) {}
}
