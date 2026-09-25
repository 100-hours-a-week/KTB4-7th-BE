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
import com.memme.dto.noti.NotificationListRequest;
import com.memme.dto.noti.NotificationListResponse;
import com.memme.dto.noti.NotificationReadRequest;
import com.memme.dto.noti.NotificationReadResponse;
import com.memme.dto.noti.NotificationReadStatus;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.noti.NotificationInboxService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class NotificationControllerTest {

    @Test
    void 로그인한_사용자의_알림을_조회하고_200을_반환한다() {
        NotificationInboxService service = mock(NotificationInboxService.class);
        NotificationController controller = new NotificationController(service);
        NotificationListRequest listRequest = new NotificationListRequest(NotificationReadStatus.ALL, null, 20);
        when(service.getNotifications(1L, listRequest)).thenReturn(listResponse());

        ResponseEntity<NotificationListResponse> result = controller.getNotifications(authenticatedRequest(), listRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("알림을 조회했습니다.", result.getBody().message());
        verify(service).getNotifications(1L, listRequest);
    }

    @Test
    void 알림_조회에_세션이_없으면_인증_예외를_던진다() {
        NotificationController controller = new NotificationController(mock(NotificationInboxService.class));

        assertThrows(
                AuthenticationRequiredException.class,
                () -> controller.getNotifications(new MockHttpServletRequest(), new NotificationListRequest(null, null, null))
        );
    }

    @Test
    void 로그인한_사용자의_알림을_읽음_처리하고_200을_반환한다() {
        NotificationInboxService service = mock(NotificationInboxService.class);
        NotificationController controller = new NotificationController(service);
        NotificationReadRequest readRequest = new NotificationReadRequest(List.of(1L));
        NotificationReadResponse readResponse = new NotificationReadResponse(1, OffsetDateTime.parse("2026-09-25T09:00:00+09:00"));
        when(service.markAsRead(1L, readRequest)).thenReturn(readResponse);

        ResponseEntity<ApiResponse<NotificationReadResponse>> result = controller.markAsRead(authenticatedRequest(), readRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("알림을 읽음 처리했습니다.", result.getBody().message());
        verify(service).markAsRead(1L, readRequest);
    }

    @Test
    void 읽음_처리할_알림이_없으면_400_fieldErrors를_반환한다() throws Exception {
        NotificationInboxService service = mock(NotificationInboxService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].message")
                        .value("읽음 처리할 알림을 선택해 주세요."));

        verifyNoInteractions(service);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);
        return request;
    }

    private NotificationListResponse listResponse() {
        NotificationListResponse.Item item = new NotificationListResponse.Item(
                1L,
                null,
                "새 솔루션이 준비되었습니다.",
                "오늘의 솔루션을 확인해 주세요.",
                "SOLUTION",
                10L,
                OffsetDateTime.parse("2026-09-25T09:00:00+09:00"),
                null
        );
        return new NotificationListResponse("알림을 조회했습니다.", null, new NotificationListResponse.Data(List.of(item)));
    }
}
