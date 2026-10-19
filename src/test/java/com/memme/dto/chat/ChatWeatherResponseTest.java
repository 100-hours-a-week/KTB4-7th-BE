package com.memme.dto.chat;

import java.util.List;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatWeatherResponseTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesReadyWeatherResponse() {
        var response = new ChatWeatherResponse("2026-10-19 11:00", "비", 14, 24,
                0.62, List.of(15, 16), 1.5, true);

        var json = mapper.readTree(mapper.writeValueAsString(ChatLookupResponse.ready(response)));

        assertThat(json.path("status").asString()).isEqualTo("READY");
        assertThat(json.path("data").path("basisDateTime").asString()).isEqualTo("2026-10-19 11:00");
        assertThat(json.path("data").path("skyText").asString()).isEqualTo("비");
        assertThat(json.path("data").path("minTempC").asInt()).isEqualTo(14);
        assertThat(json.path("data").path("maxTempC").asInt()).isEqualTo(24);
        assertThat(json.path("data").path("maxPop").asDouble()).isEqualTo(0.62);
        assertThat(json.path("data").path("rainHours").get(0).asInt()).isEqualTo(15);
        assertThat(json.path("data").path("precipitationMm").asDouble()).isEqualTo(1.5);
        assertThat(json.path("data").path("isNotable").asBoolean()).isTrue();
    }

    @Test
    void omitsOnlyUnknownPrecipitation() {
        var response = new ChatWeatherResponse("2026-10-19 11:00", "맑음", 14, 24,
                0.0, List.of(), null, false);

        var json = mapper.readTree(mapper.writeValueAsString(ChatLookupResponse.ready(response)));

        assertThat(json.path("data").has("precipitationMm")).isFalse();
        assertThat(json.path("data").path("rainHours").size()).isZero();
    }

    @Test
    void serializesEmptyWeatherResponseWithoutFakeValues() {
        var json = mapper.readTree(mapper.writeValueAsString(ChatLookupResponse.<ChatWeatherResponse>empty()));

        assertThat(json.path("status").asString()).isEqualTo("EMPTY");
        assertThat(json.has("data")).isTrue();
        assertThat(json.path("data").isNull()).isTrue();
    }

    @Test
    void rejectsReadyWithoutDataAndEmptyWithData() {
        var response = new ChatWeatherResponse("2026-10-19 11:00", "맑음", 14, 24,
                0.0, List.of(), null, false);

        assertThatThrownBy(() -> ChatLookupResponse.<ChatWeatherResponse>ready(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatLookupResponse<>(ChatLookupResponse.Status.EMPTY, response))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAndOutOfRangeFields() {
        var response = new ChatWeatherResponse("", "", null, null,
                1.2, List.of(24), -0.1, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var paths = factory.getValidator().validate(response).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .toList();
            assertThat(paths).contains("basisDateTime", "skyText", "minTempC", "maxTempC",
                    "maxPop", "rainHours[0].<list element>", "precipitationMm", "isNotable");
        }
    }
}
