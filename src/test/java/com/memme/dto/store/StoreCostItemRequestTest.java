package com.memme.dto.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StoreCostItemRequestTest {

    @Test
    void 저장할_때_세_항목은_모두_필수다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var errors = factory.getValidator().validate(new StoreCostItemRequest(null, null, null));
            assertThat(errors).extracting(error -> error.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("rentAmount", "laborAmount", "ingredientCostRate");
        }
    }

    @Test
    void 금액_음수와_소수_원가율_범위와_자릿수를_검증한다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new StoreCostItemRequest(new BigDecimal("-1"),
                    new BigDecimal("1.5"), new BigDecimal("1.0001")))).hasSize(3);
            assertThat(validator.validate(new StoreCostItemRequest(BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("0.12345")))).hasSize(1);
            assertThat(validator.validate(new StoreCostItemRequest(BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("1.0000")))).isEmpty();
        }
    }

    @Test
    void 문자열_숫자는_요청에서_거절한다() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThatThrownBy(() -> mapper.readValue(
                "{\"rentAmount\":\"1000\",\"laborAmount\":0,\"ingredientCostRate\":0.4}",
                StoreCostItemRequest.class)).hasMessageContaining("비용은 숫자로 입력해 주세요.");
    }
}
