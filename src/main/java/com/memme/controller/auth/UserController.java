package com.memme.controller.auth;

import com.memme.dto.auth.UserProfileResponse;
import com.memme.dto.auth.PasswordChangeRequest;
import com.memme.dto.common.ApiResponse;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.service.auth.PasswordChangeService;
import com.memme.service.auth.UserProfileService;
import com.memme.service.auth.WithdrawalService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final WithdrawalService withdrawalService;
    private final UserProfileService userProfileService;
    private final PasswordChangeService passwordChangeService;

    public UserController(
            WithdrawalService withdrawalService,
            UserProfileService userProfileService,
            PasswordChangeService passwordChangeService
    ) {
        this.withdrawalService = withdrawalService;
        this.userProfileService = userProfileService;
        this.passwordChangeService = passwordChangeService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(HttpServletRequest request) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        UserProfileResponse response = userProfileService.getProfile(authenticatedUser.userId());

        return ResponseEntity.ok(new ApiResponse<>("조회에 성공했습니다.", response));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            HttpServletRequest request,
            @Valid @RequestBody PasswordChangeRequest passwordChangeRequest
    ) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);
        passwordChangeService.changePassword(authenticatedUser.userId(), passwordChangeRequest);

        return ResponseEntity.ok(new ApiResponse<>("비밀번호가 변경되었습니다.", null));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(HttpServletRequest request) {
        AuthenticatedUserSession authenticatedUser = authenticatedUser(request);

        withdrawalService.withdraw(authenticatedUser.userId());
        request.getSession(false).invalidate();
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
