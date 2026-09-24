package com.memme.controller.store;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.store.StoreProfileResponse;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.service.store.StoreProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stores")
public class StoreController {

    private final StoreProfileService storeProfileService;

    public StoreController(StoreProfileService storeProfileService) {
        this.storeProfileService = storeProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StoreProfileResponse>> getProfile(HttpServletRequest request) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        StoreProfileResponse response = storeProfileService.getProfile(authenticatedUser.userId());

        return ResponseEntity.ok(new ApiResponse<>("조회에 성공했습니다.", response));
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
