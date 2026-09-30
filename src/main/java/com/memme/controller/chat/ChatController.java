package com.memme.controller.chat;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.chat.ChatHistoryResponse;
import com.memme.dto.chat.ChatMessageRequest;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.chat.ChatService;
import com.memme.service.chat.ChatStreamPlan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/v1/chat/messages")
public class ChatController {

    private static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ChatHistoryResponse getToday(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user
    ) {
        requireAuthentication(user);
        return chatService.getToday(user.userId(), user.storeId());
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> send(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @RequestBody(required = false) ChatMessageRequest request
    ) {
        requireAuthentication(user);
        ChatStreamPlan plan = chatService.prepare(user.userId(), user.storeId(), request);
        StreamingResponseBody body = outputStream -> chatService.stream(plan, outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(X_ACCEL_BUFFERING, "no")
                .body(body);
    }

    private void requireAuthentication(AuthenticatedUserSession user) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
    }
}
