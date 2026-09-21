package com.memme.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BusinessVerificationRequest(
        @NotBlank(message = "사업자등록번호를 입력해 주세요.")
        @Pattern(regexp = "^$|^\\d{10}$", message = "사업자등록번호는 숫자 10자리여야 합니다.")
        String businessRegNumber
) {
}
