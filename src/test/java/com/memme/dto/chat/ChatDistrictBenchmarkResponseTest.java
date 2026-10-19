package com.memme.dto.chat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ChatDistrictBenchmarkResponseTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesReadyDistrictResponse() {
        var response = new ChatDistrictBenchmarkResponse("2026Q2", 12_345_678,
                5, "14-17", "11-14");

        var json = mapper.readTree(mapper.writeValueAsString(ChatLookupResponse.ready(response)));

        assertThat(json.path("status").asString()).isEqualTo("READY");
        assertThat(json.path("data").path("basisQuarter").asString()).isEqualTo("2026Q2");
        assertThat(json.path("data").path("districtAvgMonthlySalesPerCafe").asInt()).isEqualTo(12_345_678);
        assertThat(json.path("data").path("cafeStoreCount").asInt()).isEqualTo(5);
        assertThat(json.path("data").path("peakSalesHourBand").asString()).isEqualTo("14-17");
        assertThat(json.path("data").path("peakFootTrafficHourBand").asString()).isEqualTo("11-14");
    }

    @Test
    void serializesEmptyDistrictResponseWithoutFakeValues() {
        var json = mapper.readTree(mapper.writeValueAsString(
                ChatLookupResponse.<ChatDistrictBenchmarkResponse>empty()));

        assertThat(json.path("status").asString()).isEqualTo("EMPTY");
        assertThat(json.has("data")).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
    }

    @Test
    void rejectsInvalidQuarterStoreCountAndBands() {
        var response = new ChatDistrictBenchmarkResponse("2026-Q2", -1,
                2, "15-18", "12-14");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var paths = factory.getValidator().validate(response).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .toList();
            assertThat(paths).contains("basisQuarter", "districtAvgMonthlySalesPerCafe",
                    "cafeStoreCount", "peakSalesHourBand", "peakFootTrafficHourBand");
        }
    }
}
