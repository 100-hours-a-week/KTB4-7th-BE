package com.memme.controller.chat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.chat.ChatMessageRequest;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.chat.ChatInsufficientDataException;
import com.memme.exception.chat.ChatRequestException;
import com.memme.service.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {

    private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 로그인_없이_SSE_챗봇을_요청하면_JSON_401을_반환한다() throws Exception {
        mockMvc.perform(post("/v1/chat/messages")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"오늘 매출은?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void 챗봇_사전_검증_오류는_SSE_Accept여도_JSON_409를_반환한다() throws Exception {
        AuthenticatedUserSession user = new AuthenticatedUserSession(1L, 2L);
        when(chatService.prepare(1L, 2L, new ChatMessageRequest("오늘 매출은?", null)))
                .thenThrow(new ChatRequestException(ChatRequestException.Reason.GENERATION_IN_PROGRESS));

        mockMvc.perform(post("/v1/chat/messages")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, user)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"오늘 매출은?\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("이전 답변을 생성하고 있습니다."));
    }

    @Test
    void 챗봇_데이터_부족_응답은_SSE_Accept여도_JSON으로_반환한다() throws Exception {
        AuthenticatedUserSession user = new AuthenticatedUserSession(1L, 2L);
        when(chatService.prepare(1L, 2L, new ChatMessageRequest("오늘 매출은?", null)))
                .thenThrow(new ChatInsufficientDataException());

        mockMvc.perform(post("/v1/chat/messages")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, user)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"오늘 매출은?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"));
    }
}
