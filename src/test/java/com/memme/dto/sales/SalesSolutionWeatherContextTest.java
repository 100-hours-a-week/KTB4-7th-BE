package com.memme.dto.sales;

import java.util.List;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSolutionWeatherContextTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesWeatherContextWithFractionalProbability() {
        var metrics = metrics(new SalesSolutionMetrics.WeatherContext(
                "2026-10-07 11:00", "비", 14, 24, 0.6,
                List.of(15, 16, 17), 5.0, true
        ));

        var json = mapper.readTree(mapper.writeValueAsString(metrics));

        assertThat(json.path("weatherContext").path("maxPop").asDouble()).isEqualTo(0.6);
        assertThat(json.path("weatherContext").path("rainHours").size()).isEqualTo(3);
        assertThat(json.path("weatherContext").path("basisDateTime").asString())
                .isEqualTo("2026-10-07 11:00");
        assertThat(json.path("weatherContext").path("isNotable").asBoolean()).isTrue();
    }

    @Test
    void omitsWeatherContextWhenUnavailable() {
        var json = mapper.readTree(mapper.writeValueAsString(metrics(null)));

        assertThat(json.has("weatherContext")).isFalse();
    }

    @Test
    void omitsOnlyPrecipitationWhenAmountIsUnknown() {
        var metrics = metrics(new SalesSolutionMetrics.WeatherContext(
                "2026-10-07 11:00", "비", 14, 24, 0.7,
                List.of(15, 16, 17), null, true
        ));

        var json = mapper.readTree(mapper.writeValueAsString(metrics));

        assertThat(json.path("weatherContext").has("precipitationMm")).isFalse();
        assertThat(json.path("weatherContext").path("maxPop").asDouble()).isEqualTo(0.7);
        assertThat(json.path("weatherContext").path("rainHours").size()).isEqualTo(3);
        assertThat(json.path("weatherContext").path("isNotable").asBoolean()).isTrue();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics)).isEmpty();
        }
    }

    @Test
    void rejectsNegativePrecipitationAmount() {
        var metrics = metrics(new SalesSolutionMetrics.WeatherContext(
                "2026-10-07 11:00", "비", 14, 24, 0.7,
                List.of(15), -0.1, true
        ));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("weatherContext.precipitationMm"));
        }
    }

    @Test
    void rejectsProbabilityOutsideFractionalRange() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (double probability : List.of(0.0, 0.6, 1.0)) {
                assertThat(validator.validate(metricsWithProbability(probability))).isEmpty();
            }
            for (double probability : List.of(-0.1, 1.1, 60.0)) {
                assertThat(validator.validate(metricsWithProbability(probability)))
                        .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("weatherContext.maxPop"));
            }
        }
    }

    private SalesSolutionMetrics metricsWithProbability(double probability) {
        return metrics(new SalesSolutionMetrics.WeatherContext(
                "2026-10-07 11:00", "비", 14, 24, probability,
                List.of(15), 5.0, true
        ));
    }

    private SalesSolutionMetrics metrics(SalesSolutionMetrics.WeatherContext weatherContext) {
        return new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), null, null, weatherContext
        );
    }
}
