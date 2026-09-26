package com.memme.dto.chat;

public record ChatMessageRequest(
        String content,
        Long retryOfMessageId
) {
}
