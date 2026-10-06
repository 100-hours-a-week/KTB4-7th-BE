package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.memme.exception.auth.InvalidLoginException;
import com.memme.exception.auth.LoginRateLimitExceededException;
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
    void 기존_세션으로_로그인에_성공하면_세션_ID를_교체한다() throws Exception {
        LoginService loginService = mock(LoginService.class);
        LoginRequest loginRequest = new LoginRequest("owner@memme.com", "password");
        when(loginService.login(loginRequest, "127.0.0.1"))
                .thenReturn(new LoginService.LoginResult(1L, "owner@memme.com", 10L));
        MockHttpSession session = new MockHttpSession();
        String previousSessionId = session.getId();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(loginService)).build();

        var result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@memme.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertNotEquals(previousSessionId, result.getRequest().getSession(false).getId());
        assertEquals(new AuthenticatedUserSession(1L, 10L),
                result.getRequest().getSession(false).getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE));
    }

    @Test
    void 세션이_없는_상태에서_로그인하면_새_인증_세션을_만든다() throws Exception {
        LoginService loginService = mock(LoginService.class);
        LoginRequest loginRequest = new LoginRequest("owner@memme.com", "password");
        when(loginService.login(loginRequest, "127.0.0.1"))
                .thenReturn(new LoginService.LoginResult(1L, "owner@memme.com", 10L));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(loginService)).build();

        var result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@memme.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(new AuthenticatedUserSession(1L, 10L),
                result.getRequest().getSession(false).getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE));
    }

    @Test
    void 로그인에_실패하면_기존_비인증_세션_ID를_유지한다() throws Exception {
        LoginService loginService = mock(LoginService.class);
        LoginRequest loginRequest = new LoginRequest("owner@memme.com", "wrong-password");
        when(loginService.login(loginRequest, "127.0.0.1")).thenThrow(new InvalidLoginException());
        MockHttpSession session = new MockHttpSession();
        String previousSessionId = session.getId();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@memme.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(previousSessionId, session.getId());
        assertNull(session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE));
    }

    @Test
    void 요청_제한에_걸리면_429와_retryAfterSeconds를_반환하고_세션을_만들지_않는다() throws Exception {
        LoginService loginService = mock(LoginService.class);
        LoginRequest loginRequest = new LoginRequest("owner@memme.com", "password");
        when(loginService.login(loginRequest, "127.0.0.1"))
                .thenThrow(new LoginRateLimitExceededException(30));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@memme.com\",\"password\":\"password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
    }

    @Test
    void 로그인_세션을_무효화하고_204_응답을_반환한다() {
        LoginService loginService = mock(LoginService.class);
        LoginController loginController = controller(loginService);
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
        LoginController loginController = controller(loginService);

        ResponseEntity<Void> response = loginController.logout(new MockHttpServletRequest());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(null, response.getBody());
    }

    @Test
    void 로그인에_성공하면_세션에_인증사용자를_저장하고_200_응답을_반환한다() {
        LoginService loginService = mock(LoginService.class);
        LoginController loginController = controller(loginService);
        LoginRequest request = new LoginRequest("owner@memme.com", "password");
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setSession(session);
        when(loginService.login(request, "127.0.0.1"))
                .thenReturn(new LoginService.LoginResult(1L, "owner@memme.com", 10L));

        ResponseEntity<ApiResponse<LoginResponse>> response = loginController.login(request, servletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("로그인에 성공했습니다.", response.getBody().message());
        assertEquals(1L, response.getBody().data().user().id());
        assertEquals("owner@memme.com", response.getBody().data().user().email());
        assertEquals(
                new AuthenticatedUserSession(1L, 10L),
                session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE)
        );
        verify(loginService).login(request, "127.0.0.1");
    }

    @Test
    void 잘못된_로그인_입력값은_400_fieldErrors로_반환한다() throws Exception {
        LoginService loginService = mock(LoginService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(loginService))
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
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'email')]"
                ).exists())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'password')]"
                ).exists());

        verifyNoInteractions(loginService);
    }

    @Test
    void 잘못된_JSON_로그인_요청은_400_공통_응답으로_반환한다() throws Exception {
        LoginService loginService = org.mockito.Mockito.mock(LoginService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"owner@memme.com\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data").doesNotExist());

        verifyNoInteractions(loginService);
    }

    private LoginController controller(LoginService loginService) {
        return new LoginController(loginService, new ClientIpResolver(false));
    }
}
