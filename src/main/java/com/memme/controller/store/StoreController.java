package com.memme.controller.store;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.store.StoreProfileResponse;
import com.memme.dto.store.StoreProfileUpdateRequest;
import com.memme.dto.store.StoreProfileUpdateResponse;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.service.store.StoreProfileService;
import com.memme.service.store.StoreProfileUpdateService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stores")
public class StoreController {

    private final StoreProfileService storeProfileService;
    private final StoreProfileUpdateService storeProfileUpdateService;

    public StoreController(
            StoreProfileService storeProfileService,
            StoreProfileUpdateService storeProfileUpdateService
    ) {
        this.storeProfileService = storeProfileService;
        this.storeProfileUpdateService = storeProfileUpdateService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StoreProfileResponse>> getProfile(HttpServletRequest request) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        StoreProfileResponse response = storeProfileService.getProfile(authenticatedUser.userId());

        return ResponseEntity.ok(new ApiResponse<>("조회에 성공했습니다.", response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<StoreProfileUpdateResponse>> updateProfile(
            HttpServletRequest request,
            @Valid @RequestBody StoreProfileUpdateRequest updateRequest
    ) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        StoreProfileUpdateResponse response = storeProfileUpdateService.updateProfile(
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
