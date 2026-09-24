package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.UserProfileResponse;
import com.memme.dto.auth.PasswordChangeRequest;
import com.memme.dto.common.ApiResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.auth.PasswordChangeService;
import com.memme.service.auth.UserProfileService;
import com.memme.service.auth.WithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class UserControllerTest {

    @Test
    void 로그인한_사용자를_탈퇴시키고_세션을_무효화한_뒤_204를_반환한다() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        PasswordChangeService passwordChangeService = mock(PasswordChangeService.class);
        UserController userController = new UserController(withdrawalService, userProfileService, passwordChangeService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);

        ResponseEntity<Void> response = userController.withdraw(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(true, session.isInvalid());
        verify(withdrawalService).withdraw(1L);
    }

    @Test
    void 인증_세션이_없으면_인증_예외를_던진다() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        PasswordChangeService passwordChangeService = mock(PasswordChangeService.class);
        UserController userController = new UserController(withdrawalService, userProfileService, passwordChangeService);

        assertThrows(AuthenticationRequiredException.class, () -> userController.withdraw(new MockHttpServletRequest()));
    }

    @Test
    void 로그인한_사용자의_내_정보를_조회하고_200을_반환한다() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        PasswordChangeService passwordChangeService = mock(PasswordChangeService.class);
        UserController userController = new UserController(withdrawalService, userProfileService, passwordChangeService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);
        when(userProfileService.getProfile(1L)).thenReturn(new UserProfileResponse(
                new UserProfileResponse.User(1L, "owner@memme.com", "01012345678", "맴매카페")
        ));

        ResponseEntity<ApiResponse<UserProfileResponse>> response = userController.getProfile(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("조회에 성공했습니다.", response.getBody().message());
        assertEquals("맴매카페", response.getBody().data().user().storeName());
        verify(userProfileService).getProfile(1L);
    }

    @Test
    void 내_정보_조회에_인증_세션이_없으면_인증_예외를_던진다() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        PasswordChangeService passwordChangeService = mock(PasswordChangeService.class);
        UserController userController = new UserController(withdrawalService, userProfileService, passwordChangeService);

        assertThrows(AuthenticationRequiredException.class, () -> userController.getProfile(new MockHttpServletRequest()));
    }

    @Test
    void 로그인한_사용자가_비밀번호를_수정하면_200을_반환한다() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        PasswordChangeService passwordChangeService = mock(PasswordChangeService.class);
        UserController userController = new UserController(withdrawalService, userProfileService, passwordChangeService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);
        PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest(
                "OldPassword1!", "NewPassword1!", "NewPassword1!"
        );

        ResponseEntity<ApiResponse<Void>> response = userController.changePassword(request, passwordChangeRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("비밀번호가 변경되었습니다.", response.getBody().message());
        verify(passwordChangeService).changePassword(1L, passwordChangeRequest);
    }

    @Test
    void 비밀번호_수정에_인증_세션이_없으면_인증_예외를_던진다() {
        WithdrawalService withdrawalService = mock(WithdrawalService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        PasswordChangeService passwordChangeService = mock(PasswordChangeService.class);
        UserController userController = new UserController(withdrawalService, userProfileService, passwordChangeService);
        PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest(
                "OldPassword1!", "NewPassword1!", "NewPassword1!"
        );

        assertThrows(
                AuthenticationRequiredException.class,
                () -> userController.changePassword(new MockHttpServletRequest(), passwordChangeRequest)
        );
    }
}
