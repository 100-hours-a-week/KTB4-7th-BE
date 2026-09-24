package com.memme.dto.sales;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesForecastContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesOfficialForecastRequestShape() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(validRequest()));

        assertThat(json.path("analysisRunId").longValue()).isEqualTo(34L);
        assertThat(json.path("forecastStartDate").asString()).isEqualTo("2026-09-08");
        assertThat(json.path("dailySales").get(0).path("date").asString())
                .isEqualTo("2026-09-07");
        assertThat(json.path("dailySales").get(0).path("amount").longValue())
                .isEqualTo(1_380_000L);
        assertThat(json.path("dailySales").get(0).path("orderCnt").longValue())
                .isEqualTo(132L);
    }

    @Test
    void serializesSuccessAndInsufficientResponses() throws Exception {
        var success = new SalesForecastBatchResponse(
                "예측을 생성했습니다.",
                null,
                new SalesForecastBatchResponse.Data(
                        LocalDate.of(2026, 9, 8),
                        LocalDate.of(2026, 10, 12),
                        35,
                        List.of(new SalesForecastBatchResponse.Prediction(
                                LocalDate.of(2026, 9, 8),
                                1_380_000L,
                                1_242_000L,
                                1_518_000L,
                                "ridge-2026-09-16")),
                        null));
        var insufficient = new SalesForecastBatchResponse(
                "매출 예측을 위해 최소 60일의 데이터가 필요합니다.",
                SalesForecastStatus.INSUFFICIENT_DATA,
                new SalesForecastBatchResponse.Data(
                        null, null, null, null, List.of("INSUFFICIENT_HISTORY")));

        var successJson = mapper.readTree(mapper.writeValueAsString(success));
        var insufficientJson = mapper.readTree(mapper.writeValueAsString(insufficient));

        assertThat(successJson.has("status")).isFalse();
        assertThat(successJson.path("data").path("horizonDays").intValue()).isEqualTo(35);
        assertThat(successJson.path("data").path("predictions").get(0).path("lowerBound").longValue())
                .isEqualTo(1_242_000L);
        assertThat(insufficientJson.path("status").asString()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(insufficientJson.path("data").path("missingData").get(0).asString())
                .isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(insufficientJson.path("data").has("predictions")).isFalse();
    }

    @Test
    void validatesRequiredRequestFieldsAndPredictionValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(validRequest())).isEmpty();

            var invalidRequest = new SalesForecastBatchRequest(
                    1L, 1L, null, null, List.of());
            assertThat(validator.validate(invalidRequest))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("analysisRunId", "forecastStartDate", "dailySales");

            var invalidPrediction = new SalesForecastBatchResponse(
                    "예측을 생성했습니다.",
                    null,
                    new SalesForecastBatchResponse.Data(
                            LocalDate.of(2026, 9, 8),
                            LocalDate.of(2026, 10, 12),
                            35,
                            List.of(new SalesForecastBatchResponse.Prediction(
                                    LocalDate.of(2026, 9, 8), -1L, 0L, 0L, "")),
                            null));
            assertThat(validator.validate(invalidPrediction))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains(
                            "data.predictions[0].predictedSalesAmount",
                            "data.predictions[0].modelVersion");
        }
    }

    private SalesForecastBatchRequest validRequest() {
        return new SalesForecastBatchRequest(
                1L,
                1L,
                34L,
                LocalDate.of(2026, 9, 8),
                List.of(new SalesForecastBatchRequest.DailySales(
                        LocalDate.of(2026, 9, 7), 1_380_000L, 132L)));
    }
}
