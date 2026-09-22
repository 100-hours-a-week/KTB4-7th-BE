package com.memme.controller.auth;

import com.memme.exception.AuthenticationRequiredException;
import com.memme.service.auth.WithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final WithdrawalService withdrawalService;

    public UserController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationRequiredException();
        }
        Object attribute = session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedUserSession authenticatedUser)) {
            throw new AuthenticationRequiredException();
        }

        withdrawalService.withdraw(authenticatedUser.userId());
        session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
