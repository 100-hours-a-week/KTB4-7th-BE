package com.memme.service.chat;

import com.memme.entity.chat.ChatMessageEntity;
import com.memme.repository.chat.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessagePersistenceService {

    private final ChatMessageRepository repository;

    public ChatMessagePersistenceService(ChatMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void startStreaming(Long messageId) {
        ChatMessageEntity message = find(messageId);
        message.startStreaming();
    }

    @Transactional
    public void complete(Long messageId, String content, String evidenceJson) {
        ChatMessageEntity message = find(messageId);
        message.complete(content, evidenceJson);
    }

    @Transactional
    public void fail(Long messageId) {
        ChatMessageEntity message = find(messageId);
        message.fail();
    }

    private ChatMessageEntity find(Long messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("챗봇 메시지를 찾을 수 없습니다: " + messageId));
    }
}
