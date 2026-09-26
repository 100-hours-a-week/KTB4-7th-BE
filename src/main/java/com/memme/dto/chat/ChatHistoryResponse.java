package com.memme.dto.chat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ChatHistoryResponse(
        String message,
        Data data
) {
    public record Data(
            LocalDate chatDate,
            String serviceGuide,
            List<Message> messages,
            List<String> recommendedQuestions
    ) {
    }

    public record Message(
            Long id,
            String role,
            String content,
            String status,
            OffsetDateTime createdAt
    ) {
    }
}
