package com.memme.dto.solution;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SolutionSaveResponse(
        String message,
        Data data
) {

    public record Data(SavedSolution savedSolution, String next) {}

    public record SavedSolution(
            Long id,
            Long solutionBundleId,
            LocalDate targetDate,
            LocalDateTime savedAt
    ) {}
}
