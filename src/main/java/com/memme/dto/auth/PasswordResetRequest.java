package com.memme.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequest(
        @NotBlank(message = "비밀번호 재설정 토큰을 입력해 주세요.")
        String token,
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,20}$",
                message = "비밀번호는 8~20자이며 대문자·소문자·숫자·특수문자를 각각 포함해야 합니다."
        )
        String newPassword,
        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        String confirmPassword
) {
}
