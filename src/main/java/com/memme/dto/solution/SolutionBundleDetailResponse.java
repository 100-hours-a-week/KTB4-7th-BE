package com.memme.dto.solution;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SolutionBundleDetailResponse(
        String message,
        Data data
) {

    public record Data(SolutionBundle solutionBundle) {}

    public record SolutionBundle(
            Long id,
            LocalDate targetDate,
            OffsetDateTime expiresAt,
            String expirationNotice,
            boolean isSaved,
            List<SolutionCardResponse> items
    ) {}
}
