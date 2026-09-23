package com.memme.dto.sales;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesInsightContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesTypedMonthlyInsightRequest() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(validRequest()));

        assertThat(json.path("targetMonth").asString()).isEqualTo("2026-09");
        assertThat(json.path("triggerType").asString()).isEqualTo("UPLOAD");
        assertThat(json.path("metrics").path("salesSummary").path("totalSales").decimalValue())
                .isEqualByComparingTo("7920000");
        assertThat(json.path("metrics").path("salesSummary").path("menuSales").decimalValue())
                .isEqualByComparingTo("7480000");
        assertThat(json.path("metrics").path("hourlySales").get(0).path("dayType").asString())
                .isEqualTo("WEEKDAY");
        assertThat(json.path("metrics").path("menuRankings").get(0).path("menuName").asString())
                .isEqualTo("아메리카노");
    }

    @Test
    void serializesSuccessInsufficientAndFailureResponsesWithoutNullFields() throws Exception {
        var success = new SalesInsightResponse(
                "매출 AI 인사이트를 생성했습니다.",
                SalesInsightStatus.COMPLETED,
                new SalesInsightResponseData(
                        YearMonth.of(2026, 9),
                        List.of("9월 총 매출은 7,920,000원입니다."),
                        null),
                null);
        var insufficient = new SalesInsightResponse(
                "인사이트 생성을 위한 매출 데이터가 부족합니다.",
                SalesInsightStatus.INSUFFICIENT_DATA,
                new SalesInsightResponseData(null, null, List.of("SALES_HISTORY")),
                null);
        var failure = new SalesInsightResponse(
                "모델 공급자 호출에 실패했습니다.",
                SalesInsightStatus.FAILED,
                null,
                new SalesInsightError("PROVIDER_ERROR", true));

        var successJson = mapper.readTree(mapper.writeValueAsString(success));
        var insufficientJson = mapper.readTree(mapper.writeValueAsString(insufficient));
        var failureJson = mapper.readTree(mapper.writeValueAsString(failure));

        assertThat(successJson.has("error")).isFalse();
        assertThat(successJson.path("data").path("targetMonth").asString()).isEqualTo("2026-09");
        assertThat(insufficientJson.path("data").path("missingData").get(0).asString())
                .isEqualTo("SALES_HISTORY");
        assertThat(insufficientJson.path("data").has("targetMonth")).isFalse();
        assertThat(failureJson.has("data")).isFalse();
        assertThat(failureJson.path("error").path("code").asString())
                .isEqualTo("PROVIDER_ERROR");
        assertThat(failureJson.path("error").path("retryable").asBoolean()).isTrue();
    }

    @Test
    void validatesRequestAndInsightLimits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(validRequest())).isEmpty();

            var invalidRequest = new SalesInsightRequest(
                    1L, 56L, 34L, YearMonth.of(2026, 9), SalesInsightTriggerType.UPLOAD,
                    validRequest().metrics(), 4);
            assertThat(validator.validate(invalidRequest))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("maxInsightCount");

            var invalidResponse = new SalesInsightResponse(
                    "매출 AI 인사이트를 생성했습니다.",
                    SalesInsightStatus.COMPLETED,
                    new SalesInsightResponseData(
                            YearMonth.of(2026, 9),
                            List.of("a", "b", "c", "d"),
                            null),
                    null);
            assertThat(validator.validate(invalidResponse))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("data.insights");
        }
    }

    private SalesInsightRequest validRequest() {
        var summary = new SalesSummaryMetric(
                new BigDecimal("7920000"),
                new BigDecimal("7480000"),
                923L,
                new BigDecimal("8581"),
                new BigDecimal("0.042"));
        var metrics = new SalesInsightMetrics(
                summary,
                List.of(new SalesTrendMetric(LocalDate.of(2026, 9, 12), new BigDecimal("2140000"), 231L)),
                List.of(new WeekdaySalesMetric(DayOfWeek.SATURDAY, new BigDecimal("1560000"), 182L)),
                List.of(new HourlySalesMetric(SalesInsightDayType.WEEKDAY, 12, new BigDecimal("420000"), 55L)),
                List.of(new CategorySalesMetric(
                        "커피", new BigDecimal("3120000"), new BigDecimal("0.417"), new BigDecimal("-0.044"))),
                List.of(new MenuRankingMetric(
                        1, "아메리카노", new BigDecimal("2108000"), 620L,
                        new BigDecimal("0.282"), new BigDecimal("0.031"))));
        return new SalesInsightRequest(
                1L,
                56L,
                34L,
                YearMonth.of(2026, 9),
                SalesInsightTriggerType.UPLOAD,
                metrics,
                3);
    }
}
