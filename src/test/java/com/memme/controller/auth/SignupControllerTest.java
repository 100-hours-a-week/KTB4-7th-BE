package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.memme.service.auth.SignupRateLimiter;
import com.memme.service.auth.SignupService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class SignupControllerTest {

    @Test
    void 잘못된_회원가입_2단계_입력값은_400_fieldErrors로_반환한다() throws Exception {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(signupService, signupBusinessService))
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
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'storeName')]"
                ).exists());

        verifyNoInteractions(signupBusinessService);
    }

    @Test
    void 회원가입_2단계_요청을_처리하고_201_응답을_반환한다() {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        SignupController signupController = controller(signupService, signupBusinessService);
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
    void 잘못된_회원가입_입력값은_400_fieldErrors로_반환한다() throws Exception {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(signupService, signupBusinessService))
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
                        .isBadRequest())
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
        SignupController signupController = controller(signupService, signupBusinessService);
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

        ResponseEntity<ApiResponse<SignupAccountResponse>> response = signupController.signupAccount(request, new MockHttpServletRequest());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("가입 정보가 임시 저장되었습니다.", response.getBody().message());
        assertEquals(serviceResponse, response.getBody().data());
        verify(signupService).signupAccount(request);
    }

    @Test
    void 같은_IP의_가입_1단계_요청이_한도를_넘으면_429와_Retry_After를_반환하고_서비스를_호출하지_않는다() throws Exception {
        SignupService signupService = mock(SignupService.class);
        SignupBusinessService signupBusinessService = mock(SignupBusinessService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        SignupRateLimiter limiter = new SignupRateLimiter(1, 60, 100,
                Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"), ZoneOffset.UTC));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SignupController(
                        signupService, signupBusinessService, limiter, new ClientIpResolver(false)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        String body = """
                {
                  "email": "owner@memme.com",
                  "password": "Password1!",
                  "passwordConfirm": "Password1!",
                  "phone": "01012345678",
                  "agreements": {
                    "termsOfService": true,
                    "termsOfServiceVersion": "2026-09",
                    "privacyPolicy": true,
                    "privacyPolicyVersion": "2026-09"
                  }
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/signup/account")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/signup/account")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));

        verify(signupService, org.mockito.Mockito.times(1)).signupAccount(org.mockito.ArgumentMatchers.any());
    }

    private SignupController controller(SignupService signupService, SignupBusinessService signupBusinessService) {
        SignupRateLimiter limiter = new SignupRateLimiter(1000, 60, 100, Clock.systemUTC());
        return new SignupController(signupService, signupBusinessService, limiter, new ClientIpResolver(false));
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
