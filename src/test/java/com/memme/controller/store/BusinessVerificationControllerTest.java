package com.memme.controller.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.dto.store.BusinessVerificationRequest;
import com.memme.dto.store.BusinessVerificationResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.store.BusinessVerificationService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BusinessVerificationControllerTest {

    @Test
    void 숫자_열자리가_아닌_사업자등록번호는_400_fieldErrors로_반환한다() throws Exception {
        BusinessVerificationService businessVerificationService = mock(
                BusinessVerificationService.class
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BusinessVerificationController(businessVerificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/business-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessRegNumber": "123-456-7890"
                                }
                                """))
                .andExpect(status()
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'businessRegNumber')]").exists());

        verifyNoInteractions(businessVerificationService);
    }

    @Test
    void 빈_사업자등록번호는_400_입력_오류_하나만_반환한다() throws Exception {
        BusinessVerificationService businessVerificationService = mock(
                BusinessVerificationService.class
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BusinessVerificationController(businessVerificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/business-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessRegNumber": ""
                                }
                                """))
                .andExpect(status()
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.data.fieldErrors.length()").value(1))
                .andExpect(jsonPath(
                        "$.data.fieldErrors[0].message").value("사업자등록번호를 입력해 주세요."));

        verifyNoInteractions(businessVerificationService);
    }

    @Test
    void 사업자등록번호_인증_요청을_처리하고_200_응답을_반환한다() {
        BusinessVerificationService businessVerificationService = mock(
                BusinessVerificationService.class
        );
        BusinessVerificationController businessVerificationController = new BusinessVerificationController(
                businessVerificationService
        );
        BusinessVerificationRequest request = new BusinessVerificationRequest("1234567890");
        BusinessVerificationResponse serviceResponse = new BusinessVerificationResponse(
                1L, OffsetDateTime.parse("2026-09-20T11:10:00+09:00")
        );
        when(businessVerificationService.verify(request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<BusinessVerificationResponse>> response = businessVerificationController.verify(
                request
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("business_number_verification_success", response.getBody().message());
        assertEquals(serviceResponse, response.getBody().data());
        verify(businessVerificationService).verify(request);
    }
}
