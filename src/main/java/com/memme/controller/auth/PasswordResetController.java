package com.memme.controller.auth;

import com.memme.dto.auth.PasswordResetEmailRequest;
import com.memme.dto.auth.PasswordResetRequest;
import com.memme.dto.common.ApiResponse;
import com.memme.service.auth.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<Void>> requestResetEmail(
            @Valid @RequestBody PasswordResetEmailRequest request
    ) {
        passwordResetService.requestResetEmail(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>("입력한 이메일로 비밀번호 재설정 안내를 보냈습니다.", null));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(new ApiResponse<>("비밀번호가 변경되었습니다.", null));
    }
}
