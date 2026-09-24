package com.memme.dto.solution;

public record SolutionCardResponse(
        Long id,
        int rankNo,
        String title,
        String summaryText,
        String detailText,
        String evidence
) {}
