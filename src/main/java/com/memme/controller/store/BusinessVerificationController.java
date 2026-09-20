package com.memme.controller.store;

import com.memme.dto.store.BusinessVerificationRequest;
import com.memme.dto.store.BusinessVerificationResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.service.store.BusinessVerificationService;
import jakarta.validation.Valid;
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
            @Valid @RequestBody BusinessVerificationRequest request
    ) {
        BusinessVerificationResponse response = businessVerificationService.verify(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("business_number_verification_success", response));
    }
}
