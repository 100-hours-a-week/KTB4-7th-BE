package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSolutionAdditionalMetricsTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesAiContractForOptionalMetrics() {
        var metrics = metrics(
                List.of(new MenuRankingMetric(1, "아이스 아메리카노", new BigDecimal("3104200"),
                        912L, new BigDecimal("0.261"), new BigDecimal("0.031"), new BigDecimal("0.205"))),
                new SalesSolutionMetrics.BusinessHours(
                        new SalesSolutionMetrics.BusinessHourBand("08:00", "22:00"),
                        new SalesSolutionMetrics.BusinessHourBand("09:00", "21:00"), List.of("MON")),
                List.of(new SalesSolutionMetrics.ConditionPattern("RAIN", 12, 1.0, 9, 38, 0.67, 0.71,
                        List.of(new SalesSolutionMetrics.ConditionMenu(
                                "아이스 아메리카노", 84, 112, -0.25, 294_000L, 392_000L)),
                        1_294_000L, 1_392_000L)));
        var json = mapper.readTree(mapper.writeValueAsString(metrics));

        assertThat(json.path("menuRankings").get(0).path("menuSales").asLong()).isEqualTo(3_104_200L);
        assertThat(json.path("menuRankings").get(0).path("ratio").asDouble()).isEqualTo(0.261);
        assertThat(json.path("menuRankings").get(0).path("previousRatio").asDouble()).isEqualTo(0.205);
        assertThat(json.path("businessHours").path("WEEKDAY").path("opensAt").asString()).isEqualTo("08:00");
        assertThat(json.path("businessHours").path("WEEKEND").path("closesAt").asString()).isEqualTo("21:00");
        assertThat(json.path("businessHours").path("closedDaysOfWeek").get(0).asString()).isEqualTo("MON");
        assertThat(json.path("conditionPatterns").get(0).path("sampleWeekdayRatio").asDouble()).isEqualTo(0.67);
        assertThat(json.path("conditionPatterns").get(0).path("menus").get(0).path("avgSales").asLong())
                .isEqualTo(294_000L);
        assertThat(json.path("conditionPatterns").get(0).path("avgNetSales").asLong())
                .isEqualTo(1_294_000L);
        assertThat(json.path("conditionPatterns").get(0).path("baselineAvgNetSales").asLong())
                .isEqualTo(1_392_000L);
    }

    @Test
    void omitsUnavailableMetricsAndKeepsWeatherConstructor() {
        var metrics = new SalesSolutionMetrics(new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), null, null,
                new SalesSolutionMetrics.WeatherContext("2026-10-07 11:00", "비", 14, 24,
                        0.6, List.of(15), null, true));
        var json = mapper.readTree(mapper.writeValueAsString(metrics));
        assertThat(json.has("menuRankings")).isFalse();
        assertThat(json.has("businessHours")).isFalse();
        assertThat(json.has("conditionPatterns")).isFalse();
        assertThat(json.has("weatherContext")).isTrue();
    }

    @Test
    void validatesConditionPatternSampleRatiosAndMenuLimit() {
        var menu = new SalesSolutionMetrics.ConditionMenu("메뉴", 1, 3, -0.5, 100L, 200L);
        var metrics = metrics(null, null,
                List.of(new SalesSolutionMetrics.ConditionPattern("RAIN", 12, 1.0, 4, 5, 1.2, 0.7,
                        List.of(menu, menu, menu, menu))));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(metrics);
            assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("sampleDays"));
            assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("sampleWeekdayRatio"));
            assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("menus"));
        }
    }

    @Test
    void acceptsAndSerializesTenMenuRankings() {
        var metrics = metrics(menuRankings(10), null, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics)).isEmpty();
        }

        assertThat(mapper.readTree(mapper.writeValueAsString(metrics)).path("menuRankings").size()).isEqualTo(10);
    }

    @Test
    void rejectsElevenMenuRankings() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics(menuRankings(11), null, null)))
                    .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("menuRankings"));
        }
    }

    @Test
    void acceptsTwentyFourHourBusinessHoursWithoutClosedDays() {
        var metrics = metrics(null, new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("00:00", "24:00"),
                new SalesSolutionMetrics.BusinessHourBand("00:00", "24:00"), null), null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics)).isEmpty();
        }
        assertThat(mapper.readTree(mapper.writeValueAsString(metrics))
                .path("businessHours").has("closedDaysOfWeek")).isFalse();
    }

    @Test
    void rejectsMalformedBusinessHoursAndClosedDayAbbreviation() {
        var metrics = metrics(null, new SalesSolutionMetrics.BusinessHours(
                new SalesSolutionMetrics.BusinessHourBand("9:00", "22:00"),
                new SalesSolutionMetrics.BusinessHourBand("09:00", "21:00"), List.of("MONDAY")), null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(metrics);
            assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("opensAt"));
            assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("closedDaysOfWeek"));
        }
    }

    private MenuRankingMetric ranking(int rank) {
        return new MenuRankingMetric(rank, "메뉴" + rank, BigDecimal.ONE, 1L,
                new BigDecimal("0.1"), null);
    }

    private List<MenuRankingMetric> menuRankings(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(this::ranking).toList();
    }

    private SalesSolutionMetrics metrics(List<MenuRankingMetric> menuRankings,
                                         SalesSolutionMetrics.BusinessHours businessHours,
                                         List<SalesSolutionMetrics.ConditionPattern> conditionPatterns) {
        return new SalesSolutionMetrics(new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), null, null, null, menuRankings, businessHours, conditionPatterns);
    }
}
