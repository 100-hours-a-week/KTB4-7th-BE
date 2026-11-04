package com.memme.controller.noti;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.noti.FcmTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FcmTokenControllerTest {

    private FcmTokenService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FcmTokenService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FcmTokenController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void 세션_사용자의_FCM_토큰을_등록하고_본문_없는_204를_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v3/notifications/fcm-tokens")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"browser-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).registerToken(17L, "browser-token");
    }

    @Test
    void 세션_사용자_토큰만_해제하고_본문_없는_204를_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/v3/notifications/fcm-tokens")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"browser-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).unregisterToken(17L, "browser-token");
    }

    @Test
    void 세션이_없으면_401을_반환하고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v3/notifications/fcm-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"browser-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 빈_토큰은_400으로_거절하고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v3/notifications/fcm-tokens")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."));

        verifyNoInteractions(service);
    }

    @Test
    void 최대_길이를_초과한_토큰은_400으로_거절한다() throws Exception {
        String tooLongToken = "x".repeat(4097);

        mockMvc.perform(MockMvcRequestBuilders.delete("/v3/notifications/fcm-tokens")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tooLongToken + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthenticatedUserSession.SESSION_ATTRIBUTE,
                new AuthenticatedUserSession(17L, 10L)
        );
        return session;
    }
}
