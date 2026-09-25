package com.memme.controller.noti;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.noti.NotificationListRequest;
import com.memme.dto.noti.NotificationListResponse;
import com.memme.dto.noti.NotificationReadRequest;
import com.memme.dto.noti.NotificationReadResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.noti.NotificationInboxService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private static final String READ_SUCCESS_MESSAGE = "알림을 읽음 처리했습니다.";

    private final NotificationInboxService notificationInboxService;

    public NotificationController(NotificationInboxService notificationInboxService) {
        this.notificationInboxService = notificationInboxService;
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> getNotifications(
            HttpServletRequest request,
            @Valid @ModelAttribute NotificationListRequest listRequest
    ) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        return ResponseEntity.ok(notificationInboxService.getNotifications(authenticatedUser.userId(), listRequest));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsRead(
            HttpServletRequest request,
            @Valid @RequestBody NotificationReadRequest readRequest
    ) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        NotificationReadResponse response = notificationInboxService.markAsRead(authenticatedUser.userId(), readRequest);
        return ResponseEntity.ok(new ApiResponse<>(READ_SUCCESS_MESSAGE, response));
    }

    private AuthenticatedUserSession authenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationRequiredException();
        }
        Object attribute = session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedUserSession authenticatedUser)) {
            throw new AuthenticationRequiredException();
        }
        return authenticatedUser;
    }
}
