package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.dto.auth.LoginRequest;
import com.memme.dto.auth.LoginResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.auth.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class LoginControllerTest {

    @Test
    void 로그인_세션을_무효화하고_204_응답을_반환한다() {
        LoginService loginService = mock(LoginService.class);
        LoginController loginController = new LoginController(loginService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        ResponseEntity<Void> response = loginController.logout(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(true, session.isInvalid());
        assertEquals(null, response.getBody());
    }

    @Test
    void 로그인_세션이_없어도_204_응답을_반환한다() {
        LoginService loginService = mock(LoginService.class);
        LoginController loginController = new LoginController(loginService);

        ResponseEntity<Void> response = loginController.logout(new MockHttpServletRequest());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(null, response.getBody());
    }

    @Test
    void 로그인에_성공하면_세션에_인증사용자를_저장하고_200_응답을_반환한다() {
        LoginService loginService = mock(LoginService.class);
        LoginController loginController = new LoginController(loginService);
        LoginRequest request = new LoginRequest("owner@memme.com", "password");
        MockHttpSession session = new MockHttpSession();
        when(loginService.login(request)).thenReturn(new LoginService.LoginResult(1L, "owner@memme.com", 10L));

        ResponseEntity<ApiResponse<LoginResponse>> response = loginController.login(request, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("로그인에 성공했습니다.", response.getBody().message());
        assertEquals(1L, response.getBody().data().user().id());
        assertEquals("owner@memme.com", response.getBody().data().user().email());
        assertEquals(
                new AuthenticatedUserSession(1L, 10L),
                session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE)
        );
        verify(loginService).login(request);
    }

    @Test
    void 잘못된_로그인_입력값은_422_fieldErrors로_반환한다() throws Exception {
        LoginService loginService = mock(LoginService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status()
                        .isUnprocessableContent())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'email')]"
                ).exists())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'password')]"
                ).exists());

        verifyNoInteractions(loginService);
    }
}
