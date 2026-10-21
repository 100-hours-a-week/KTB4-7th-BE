package com.memme.service.solution;

import java.time.LocalDateTime;

public record SolutionRegenerationReservation(
        SolutionRegenerationOutcome outcome, Long executionId, int attempt, LocalDateTime startedAt) {

    public static SolutionRegenerationReservation rejected(SolutionRegenerationOutcome outcome) {
        return new SolutionRegenerationReservation(outcome, null, 0, null);
    }

    public static SolutionRegenerationReservation acquired(Long id, int attempt, LocalDateTime startedAt) {
        return new SolutionRegenerationReservation(SolutionRegenerationOutcome.RESERVED, id, attempt, startedAt);
    }
}
