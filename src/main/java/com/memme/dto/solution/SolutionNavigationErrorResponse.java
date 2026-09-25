package com.memme.dto.solution;

public record SolutionNavigationErrorResponse(
        String message,
        String next,
        Void data
) {}
