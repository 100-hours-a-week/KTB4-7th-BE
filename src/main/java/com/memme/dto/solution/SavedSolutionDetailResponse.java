package com.memme.dto.solution;

import java.time.LocalDate;
import java.util.List;

public record SavedSolutionDetailResponse(
        String message,
        Data data
) {

    public record Data(
            SavedSolution savedSolution
    ) {}

    public record SavedSolution(
            Long id,
            LocalDate savedDate,
            String displayTitle,
            boolean readOnly,
            List<Item> items
    ) {}

    public record Item(
            int rankNo,
            String title,
            String summaryText,
            String detailText,
            String evidence
    ) {}
}
