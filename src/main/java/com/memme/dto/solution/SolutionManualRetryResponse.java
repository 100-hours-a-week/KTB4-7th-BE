package com.memme.dto.solution;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SolutionManualRetryResponse(
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String status,
        Data data
) {

    public record Data(Long solutionBundleId) {}
}
