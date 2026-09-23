package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.dto.auth.SignupAccountResponse;
import com.memme.dto.auth.SignupBusinessRequest;
import com.memme.dto.auth.SignupBusinessResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.auth.SignupBusinessService;
import com.memme.service.auth.SignupService;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;
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
    void 잘못된_회원가입_2단계_입력값은_422_fieldErrors로_반환한다() throws Exception {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SignupController(signupService, signupBusinessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/signup/business")
                        .header("Signup-Token", "signup_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeName": "",
                                  "businessRegNumber": "123",
                                  "businessVerificationId": 1,
                                  "postalCode": "123",
                                  "address": "",
                                  "businessHours": []
                                }
                                """))
                .andExpect(status()
                        .isUnprocessableContent())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'storeName')]"
                ).exists());

        verifyNoInteractions(signupBusinessService);
    }

    @Test
    void 회원가입_2단계_요청을_처리하고_201_응답을_반환한다() {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        SignupController signupController = new SignupController(signupService, signupBusinessService);
        SignupBusinessRequest request = validBusinessRequest();
        SignupBusinessResponse serviceResponse = new SignupBusinessResponse(
                new SignupBusinessResponse.User(1L, "owner@memme.com"),
                new SignupBusinessResponse.Store(2L, "맴매 분식"),
                "LOGIN"
        );
        when(signupBusinessService.completeSignup("signup_token", request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<SignupBusinessResponse>> response = signupController.signupBusiness(
                "signup_token", request
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("회원가입이 완료되었습니다.", response.getBody().message());
        assertEquals(serviceResponse, response.getBody().data());
        verify(signupBusinessService).completeSignup("signup_token", request);
    }

    @Test
    void 잘못된_회원가입_입력값은_422_fieldErrors로_반환한다() throws Exception {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SignupController(signupService, signupBusinessService))
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
                .andExpect(status()
                        .isUnprocessableContent())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'email')]"
                ).exists())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'agreements.termsOfService')]"
                ).exists());

        verifyNoInteractions(signupService);
    }

    @Test
    void 회원가입_1단계_요청을_처리하고_201_응답을_반환한다() {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        SignupController signupController = new SignupController(signupService, signupBusinessService);
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

    private SignupBusinessRequest validBusinessRequest() {
        return new SignupBusinessRequest(
                "맴매 분식",
                "1234567890",
                1L,
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                List.of(
                        businessHours(DayOfWeek.MONDAY),
                        businessHours(DayOfWeek.TUESDAY),
                        businessHours(DayOfWeek.WEDNESDAY),
                        businessHours(DayOfWeek.THURSDAY),
                        businessHours(DayOfWeek.FRIDAY),
                        businessHours(DayOfWeek.SATURDAY),
                        businessHours(DayOfWeek.SUNDAY)
                )
        );
    }

    private SignupBusinessRequest.BusinessHours businessHours(DayOfWeek dayOfWeek) {
        return new SignupBusinessRequest.BusinessHours(dayOfWeek, false, "09:00", "18:00");
    }
}
