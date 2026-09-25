package com.memme.controller.noti;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.noti.NotificationPreferenceResponse;
import com.memme.dto.noti.NotificationPreferenceUpdateRequest;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.noti.NotificationPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class NotificationPreferenceControllerTest {

    @Test
    void 로그인한_사용자의_알림_설정을_조회하고_200을_반환한다() {
        NotificationPreferenceService service = mock(NotificationPreferenceService.class);
        NotificationPreferenceController controller = new NotificationPreferenceController(service);
        MockHttpServletRequest request = authenticatedRequest();
        when(service.getPreferences(1L)).thenReturn(response());

        ResponseEntity<ApiResponse<NotificationPreferenceResponse>> result = controller.getPreferences(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("조회에 성공했습니다.", result.getBody().message());
        verify(service).getPreferences(1L);
    }

    @Test
    void 알림_설정_조회에_세션이_없으면_인증_예외를_던진다() {
        NotificationPreferenceController controller = new NotificationPreferenceController(mock(NotificationPreferenceService.class));

        assertThrows(AuthenticationRequiredException.class, () -> controller.getPreferences(new MockHttpServletRequest()));
    }

    @Test
    void 로그인한_사용자의_알림_설정을_수정하고_200을_반환한다() {
        NotificationPreferenceService service = mock(NotificationPreferenceService.class);
        NotificationPreferenceController controller = new NotificationPreferenceController(service);
        MockHttpServletRequest request = authenticatedRequest();
        NotificationPreferenceUpdateRequest updateRequest = new NotificationPreferenceUpdateRequest(true, null);
        when(service.updatePreferences(1L, updateRequest)).thenReturn(response());

        ResponseEntity<ApiResponse<NotificationPreferenceResponse>> result = controller.updatePreferences(request, updateRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("수정되었습니다.", result.getBody().message());
        verify(service).updatePreferences(1L, updateRequest);
    }

    @Test
    void 알림_설정_수정에_세션이_없으면_인증_예외를_던진다() {
        NotificationPreferenceController controller = new NotificationPreferenceController(mock(NotificationPreferenceService.class));
        NotificationPreferenceUpdateRequest updateRequest = new NotificationPreferenceUpdateRequest(true, null);

        assertThrows(
                AuthenticationRequiredException.class,
                () -> controller.updatePreferences(new MockHttpServletRequest(), updateRequest)
        );
    }

    @Test
    void 수정할_알림_설정이_없으면_400_fieldErrors를_반환한다() throws Exception {
        NotificationPreferenceService service = mock(NotificationPreferenceService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationPreferenceController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].message")
                        .value("수정할 알림 설정을 하나 이상 입력해 주세요."));

        verifyNoInteractions(service);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);
        return request;
    }

    private NotificationPreferenceResponse response() {
        return new NotificationPreferenceResponse(new NotificationPreferenceResponse.Preferences(true, false));
    }
}
