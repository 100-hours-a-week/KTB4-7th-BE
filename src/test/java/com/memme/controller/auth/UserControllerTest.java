package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.memme.exception.AuthenticationRequiredException;
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
        UserController userController = new UserController(withdrawalService);
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
        UserController userController = new UserController(withdrawalService);

        assertThrows(AuthenticationRequiredException.class, () -> userController.withdraw(new MockHttpServletRequest()));
    }
}
