package com.memme.dto.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.util.List;

public record StoreProfileUpdateRequest(
        @Size(max = 15, message = "매장명은 15자 이하여야 합니다.")
        @Pattern(regexp = "^[가-힣A-Za-z0-9 ,&·-]+$", message = "매장명에 사용할 수 없는 문자가 포함되어 있습니다.")
        String storeName,
        @Valid
        Address address,
        @Size(min = 7, max = 7, message = "영업시간은 월요일부터 일요일까지 7건을 입력해 주세요.")
        List<@Valid BusinessHours> businessHours
) {

    public record Address(
            @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
            String postalCode,
            @Size(min = 1, max = 255, message = "기본 주소는 255자 이하여야 합니다.")
            String roadAddress,
            @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
            String addressDetail
    ) {
    }

    public record BusinessHours(
            @NotNull(message = "요일을 입력해 주세요.")
            DayOfWeek dayOfWeek,
            @NotNull(message = "휴무 여부를 입력해 주세요.")
            Boolean isClosed,
            @Pattern(
                    regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d$",
                    message = "영업 시작 시간은 HH:mm 형식이어야 합니다."
            )
            String openTime,
            @Pattern(
                    regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d$",
                    message = "영업 마감 시간은 HH:mm 형식이어야 합니다."
            )
            String closeTime
    ) {
    }
}
