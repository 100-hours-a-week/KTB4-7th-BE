package com.memme.controller.noti;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.noti.NotificationPreferenceResponse;
import com.memme.dto.noti.NotificationPreferenceUpdateRequest;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.noti.NotificationPreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    public NotificationPreferenceController(NotificationPreferenceService notificationPreferenceService) {
        this.notificationPreferenceService = notificationPreferenceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(HttpServletRequest request) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        NotificationPreferenceResponse response = notificationPreferenceService.getPreferences(authenticatedUser.userId());

        return ResponseEntity.ok(new ApiResponse<>("조회에 성공했습니다.", response));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            HttpServletRequest request,
            @Valid @RequestBody NotificationPreferenceUpdateRequest updateRequest
    ) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        NotificationPreferenceResponse response = notificationPreferenceService.updatePreferences(
                authenticatedUser.userId(), updateRequest
        );

        return ResponseEntity.ok(new ApiResponse<>("수정되었습니다.", response));
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
