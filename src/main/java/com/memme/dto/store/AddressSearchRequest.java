package com.memme.dto.store;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddressSearchRequest(
        @NotBlank(message = "주소 검색어를 입력해 주세요.")
        String query,
        String cursor,
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 10, message = "size는 10 이하여야 합니다.")
        Integer size
) {
}
