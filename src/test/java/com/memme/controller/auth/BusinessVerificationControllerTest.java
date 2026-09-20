package com.memme.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.BusinessVerificationRequest;
import com.memme.dto.auth.BusinessVerificationResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.service.auth.BusinessVerificationService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BusinessVerificationControllerTest {

    @Test
    void 사업자등록번호_인증_요청을_처리하고_200_응답을_반환한다() {
        BusinessVerificationService businessVerificationService = org.mockito.Mockito.mock(
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
