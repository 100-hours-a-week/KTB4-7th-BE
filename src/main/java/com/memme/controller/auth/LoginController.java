package com.memme.controller.auth;

import com.memme.dto.auth.LoginRequest;
import com.memme.dto.auth.LoginResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.service.auth.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpSession session
    ) {
        LoginService.LoginResult result = loginService.login(request);
        session.setAttribute(
                AuthenticatedUserSession.SESSION_ATTRIBUTE,
                new AuthenticatedUserSession(result.userId(), result.storeId())
        );

        LoginResponse response = new LoginResponse(new LoginResponse.User(result.userId(), result.email()));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("로그인에 성공했습니다.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
