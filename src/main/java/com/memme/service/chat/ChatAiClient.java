package com.memme.service.chat;

import java.util.function.Consumer;

import com.memme.dto.chat.ChatAiRequest;

public interface ChatAiClient {

    void stream(ChatAiRequest request, Consumer<ChatAiEvent> eventConsumer);
}
