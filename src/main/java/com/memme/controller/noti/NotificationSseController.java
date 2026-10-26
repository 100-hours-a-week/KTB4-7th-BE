package com.memme.controller.noti;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.noti.NotificationSseConnectionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationSseController {

    private static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";

    private final NotificationSseConnectionService connectionService;

    public NotificationSseController(NotificationSseConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connect(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user
    ) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(X_ACCEL_BUFFERING, "no")
                .body(connectionService.connect(user.userId()));
    }
}
