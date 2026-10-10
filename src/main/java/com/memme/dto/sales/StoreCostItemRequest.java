package com.memme.dto.sales;

import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StoreCostItemRequest(
        @NotNull(message = "임대료를 입력해 주세요.")
        @DecimalMin(value = "0", message = "임대료는 0 이상의 금액으로 입력해 주세요.")
        @DecimalMax(value = "9223372036854775807", message = "임대료가 허용 범위를 초과했습니다.")
        @Digits(integer = 19, fraction = 0, message = "임대료는 원 단위 정수로 입력해 주세요.")
        @JsonDeserialize(using = StrictCostNumberDeserializer.class)
        BigDecimal rentAmount,
        @NotNull(message = "인건비를 입력해 주세요.")
        @DecimalMin(value = "0", message = "인건비는 0 이상의 금액으로 입력해 주세요.")
        @DecimalMax(value = "9223372036854775807", message = "인건비가 허용 범위를 초과했습니다.")
        @Digits(integer = 19, fraction = 0, message = "인건비는 원 단위 정수로 입력해 주세요.")
        @JsonDeserialize(using = StrictCostNumberDeserializer.class)
        BigDecimal laborAmount,
        @NotNull(message = "원가율을 입력해 주세요.")
        @DecimalMin(value = "0", message = "원가율은 0에서 100 사이로 입력해주세요.")
        @DecimalMax(value = "100", message = "원가율은 0에서 100 사이로 입력해주세요.")
        @Digits(integer = 3, fraction = 1, message = "원가율은 소수점 첫째 자리까지 입력할 수 있습니다.")
        @JsonDeserialize(using = StrictCostNumberDeserializer.class)
        BigDecimal ingredientCostRate
) {
}
