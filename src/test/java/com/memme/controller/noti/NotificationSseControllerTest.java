package com.memme.controller.noti;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.noti.NotificationSseConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseControllerTest {

    private NotificationSseConnectionService connectionService;
    private MockMvc mockMvc;
    private SseEmitter emitter;

    @BeforeEach
    void setUp() {
        connectionService = mock(NotificationSseConnectionService.class);
        emitter = new SseEmitter();
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationSseController(connectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 인증된_사용자는_알림_SSE_스트림에_연결된다() throws Exception {
        when(connectionService.connect(42L)).thenReturn(emitter);

        mockMvc.perform(get("/v1/notifications/stream")
                        .sessionAttr(
                                AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(42L, 7L)
                        )
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(request().asyncStarted());

        verify(connectionService).connect(42L);
    }

    @Test
    void 세션이_없으면_401을_반환하고_SSE_연결을_만들지_않는다() throws Exception {
        mockMvc.perform(get("/v1/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        verifyNoInteractions(connectionService);
    }
}
