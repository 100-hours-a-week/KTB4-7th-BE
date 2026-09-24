package com.memme.dto.solution;

import java.time.LocalDate;
import java.util.List;

public record SavedSolutionListResponse(
        String message,
        Long nextCursor,
        Data data
) {

    public record Data(List<YearGroup> groups) {}

    public record YearGroup(int year, List<Item> items) {}

    public record Item(
            Long savedId,
            LocalDate savedDate,
            String displayTitle,
            String firstTitle,
            int remainingItemCount
    ) {}
}
