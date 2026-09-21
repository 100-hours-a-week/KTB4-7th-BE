package com.memme.dto.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.util.List;

public record SignupBusinessRequest(
        @NotBlank(message = "매장명을 입력해 주세요.")
        @Size(max = 15, message = "매장명은 15자 이하여야 합니다.")
        @Pattern(
                regexp = "^$|^[가-힣A-Za-z0-9 ,&·-]+$",
                message = "매장명에 사용할 수 없는 문자가 포함되어 있습니다."
        )
        String storeName,
        @NotBlank(message = "사업자등록번호를 입력해 주세요.")
        @Pattern(regexp = "^$|^\\d{10}$", message = "사업자등록번호는 숫자 10자리여야 합니다.")
        String businessRegNumber,
        @NotNull(message = "사업자 인증 결과를 입력해 주세요.")
        @Positive(message = "사업자 인증 결과를 확인해 주세요.")
        Long businessVerificationId,
        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Pattern(regexp = "^$|^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
        String postalCode,
        @NotBlank(message = "기본 주소를 입력해 주세요.")
        @Size(max = 255, message = "기본 주소는 255자 이하여야 합니다.")
        String address,
        @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
        String addressDetail,
        @NotNull(message = "영업시간을 입력해 주세요.")
        @Size(min = 7, max = 7, message = "영업시간은 월요일부터 일요일까지 7건을 입력해 주세요.")
        List<@Valid BusinessHours> businessHours
) {

    public record BusinessHours(
            @NotNull(message = "요일을 입력해 주세요.")
            DayOfWeek dayOfWeek,
            @NotNull(message = "휴무 여부를 입력해 주세요.")
            Boolean isClosed,
            @Pattern(
                    regexp = "^$|^(?:[01]\\d|2[0-3]):[0-5]\\d$",
                    message = "영업 시작 시간은 HH:mm 형식이어야 합니다."
            )
            String openTime,
            @Pattern(
                    regexp = "^$|^(?:[01]\\d|2[0-3]):[0-5]\\d$",
                    message = "영업 마감 시간은 HH:mm 형식이어야 합니다."
            )
            String closeTime
    ) {
    }
}
