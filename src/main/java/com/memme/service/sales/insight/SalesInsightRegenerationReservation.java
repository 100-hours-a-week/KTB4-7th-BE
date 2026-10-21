package com.memme.service.sales.insight;

import java.time.LocalDateTime;

public record SalesInsightRegenerationReservation(
        SalesInsightRegenerationOutcome outcome, Long executionId, int attempt, LocalDateTime startedAt) {

    public static SalesInsightRegenerationReservation rejected(SalesInsightRegenerationOutcome outcome) {
        return new SalesInsightRegenerationReservation(outcome, null, 0, null);
    }

    public static SalesInsightRegenerationReservation acquired(Long id, int attempt, LocalDateTime startedAt) {
        return new SalesInsightRegenerationReservation(SalesInsightRegenerationOutcome.RESERVED, id, attempt,
                startedAt);
    }
}
