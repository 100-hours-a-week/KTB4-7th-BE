package com.memme.dto.sales;

import java.io.IOException;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesDtoContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    @TestFactory
    Stream<DynamicTest> matchesSheetExamples() throws IOException {
        try (var input = getClass().getResourceAsStream("/sales/dto/v1-contract.json")) {
            var examples = mapper.readTree(input);
            return examples.valueStream().map(example -> DynamicTest.dynamicTest(
                    example.path("cell").asString(), () -> {
                        var wrapper = Class.forName(example.path("wrapper").asString());
                        var dataType = example.path("dataType");
                        var type = dataType.isNull()
                                ? mapper.constructType(wrapper)
                                : mapper.getTypeFactory().constructParametricType(
                                        wrapper, Class.forName(dataType.asString()));
                        var expected = example.path("json");
                        Object dto = mapper.readValue(expected.toString(), type);
                        var actual = mapper.readTree(mapper.writeValueAsString(dto));
                        assertThat(actual).isEqualTo(expected);
                    }));
        }
    }

    @Test
    void defaultsHistoryPagination() {
        var request = new SalesUploadHistoryRequest(null, null, null);
        assertThat(request.page()).isEqualTo(1);
        assertThat(request.size()).isEqualTo(10);
    }

    @Test
    void rejectsHistoryPagesOutsideOneToFiveAndSizeOtherThanTen() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new SalesUploadHistoryRequest(null, 1, 10))).isEmpty();
            assertThat(validator.validate(new SalesUploadHistoryRequest(null, 5, 10))).isEmpty();
            assertThat(validator.validate(new SalesUploadHistoryRequest(null, 0, 10))).hasSize(1);
            assertThat(validator.validate(new SalesUploadHistoryRequest(null, 6, 10))).hasSize(1);
            assertThat(validator.validate(new SalesUploadHistoryRequest(null, 1, 9))).hasSize(1);
            assertThat(validator.validate(new SalesUploadHistoryRequest(null, 1, 11))).hasSize(1);
        }
    }
}
