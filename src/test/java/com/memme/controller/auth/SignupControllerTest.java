package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.dto.auth.SignupAccountResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.auth.SignupService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class SignupControllerTest {

    @Test
    void 잘못된_회원가입_입력값은_422_fieldErrors로_반환한다() throws Exception {
        SignupService signupService = org.mockito.Mockito.mock(SignupService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SignupController(signupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/signup/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "password1!",
                                  "passwordConfirm": "password1!",
                                  "phone": "01112345678",
                                  "agreements": {
                                    "termsOfService": false,
                                    "termsOfServiceVersion": "",
                                    "privacyPolicy": false,
                                    "privacyPolicyVersion": ""
                                  }
                                }
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnprocessableContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.fieldErrors[?(@.field == 'email')]"
                ).exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.fieldErrors[?(@.field == 'agreements.termsOfService')]"
                ).exists());

        verifyNoInteractions(signupService);
    }

    @Test
    void 회원가입_1단계_요청을_처리하고_201_응답을_반환한다() {
        SignupService signupService = org.mockito.Mockito.mock(SignupService.class);
        SignupController signupController = new SignupController(signupService);
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com",
                "Password1!",
                "Password1!",
                "01012345678",
                new SignupAccountRequest.Agreements(true, "2026-09", true, "2026-09")
        );
        SignupAccountResponse serviceResponse = new SignupAccountResponse(
                "signup_token", OffsetDateTime.parse("2026-09-17T11:00:00+09:00")
        );
        when(signupService.signupAccount(request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<SignupAccountResponse>> response = signupController.signupAccount(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("가입 정보가 임시 저장되었습니다.", response.getBody().message());
        assertEquals(serviceResponse, response.getBody().data());
        verify(signupService).signupAccount(request);
    }
}
