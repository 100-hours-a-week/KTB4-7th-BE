package com.memme.dto.chat;

import java.time.LocalDate;
import java.util.List;

public record ChatAiRequest(
        Long userId,
        Long storeId,
        LocalDate chatDate,
        String question,
        List<History> history,
        List<ContextCard> context
) {
    public record History(
            String role,
            String content
    ) {
    }

    public record ContextCard(
            int rankNo,
            String title,
            String summaryText,
            String detailText,
            String evidence
    ) {
    }
}
