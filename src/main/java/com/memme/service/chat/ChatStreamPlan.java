package com.memme.service.chat;

import com.memme.dto.chat.ChatAiRequest;

public record ChatStreamPlan(
        Long assistantMessageId,
        ChatAiRequest aiRequest
) {
}
