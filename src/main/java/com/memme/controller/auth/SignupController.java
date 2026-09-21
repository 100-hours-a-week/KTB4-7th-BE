package com.memme.controller.auth;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.dto.auth.SignupAccountResponse;
import com.memme.dto.auth.SignupBusinessRequest;
import com.memme.dto.auth.SignupBusinessResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.service.auth.SignupBusinessService;
import com.memme.service.auth.SignupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/signup")
public class SignupController {

    private final SignupService signupService;
    private final SignupBusinessService signupBusinessService;

    public SignupController(SignupService signupService, SignupBusinessService signupBusinessService) {
        this.signupService = signupService;
        this.signupBusinessService = signupBusinessService;
    }

    @PostMapping("/account")
    public ResponseEntity<ApiResponse<SignupAccountResponse>> signupAccount(
            @Valid @RequestBody SignupAccountRequest request
    ) {
        SignupAccountResponse response = signupService.signupAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("가입 정보가 임시 저장되었습니다.", response));
    }

    @PostMapping("/business")
    public ResponseEntity<ApiResponse<SignupBusinessResponse>> signupBusiness(
            @RequestHeader("Signup-Token") String signupToken,
            @Valid @RequestBody SignupBusinessRequest request
    ) {
        SignupBusinessResponse response = signupBusinessService.completeSignup(signupToken, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("회원가입이 완료되었습니다.", response));
    }
}
