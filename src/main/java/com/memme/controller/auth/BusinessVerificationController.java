package com.memme.controller.auth;

import com.memme.dto.auth.BusinessVerificationRequest;
import com.memme.dto.auth.BusinessVerificationResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.service.auth.BusinessVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/business-verifications")
public class BusinessVerificationController {

    private final BusinessVerificationService businessVerificationService;

    public BusinessVerificationController(BusinessVerificationService businessVerificationService) {
        this.businessVerificationService = businessVerificationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BusinessVerificationResponse>> verify(
            @RequestBody BusinessVerificationRequest request
    ) {
        BusinessVerificationResponse response = businessVerificationService.verify(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("business_number_verification_success", response));
    }
}
