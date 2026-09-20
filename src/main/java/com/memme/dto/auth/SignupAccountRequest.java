package com.memme.dto.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SignupAccountRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식을 입력해 주세요.")
        String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,20}$",
                message = "비밀번호는 8~20자이며 대문자·소문자·숫자·특수문자를 각각 포함해야 합니다."
        )
        String password,
        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        String passwordConfirm,
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = "^010\\d{8}$", message = "010으로 시작하는 11자리 휴대폰 번호를 입력해 주세요.")
        String phone,
        @NotNull(message = "약관 동의 정보를 입력해 주세요.")
        @Valid Agreements agreements
) {

    public record Agreements(
            @AssertTrue(message = "이용약관에 동의해 주세요.")
            boolean termsOfService,
            @NotBlank(message = "이용약관 버전을 입력해 주세요.")
            String termsOfServiceVersion,
            @AssertTrue(message = "개인정보처리방침에 동의해 주세요.")
            boolean privacyPolicy,
            @NotBlank(message = "개인정보처리방침 버전을 입력해 주세요.")
            String privacyPolicyVersion
    ) {
    }
}
