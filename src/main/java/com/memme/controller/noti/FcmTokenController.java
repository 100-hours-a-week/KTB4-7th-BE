package com.memme.controller.noti;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.noti.FcmTokenRequest;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.noti.FcmTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v3/notifications/fcm-tokens")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    public FcmTokenController(FcmTokenService fcmTokenService) {
        this.fcmTokenService = fcmTokenService;
    }

    @PostMapping
    public ResponseEntity<Void> registerToken(
            HttpServletRequest request,
            @Valid @RequestBody FcmTokenRequest tokenRequest
    ) {
        AuthenticatedUserSession user = authenticatedUser(request);
        fcmTokenService.registerToken(user.userId(), tokenRequest.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregisterToken(
            HttpServletRequest request,
            @Valid @RequestBody FcmTokenRequest tokenRequest
    ) {
        AuthenticatedUserSession user = authenticatedUser(request);
        fcmTokenService.unregisterToken(user.userId(), tokenRequest.token());
        return ResponseEntity.noContent().build();
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
