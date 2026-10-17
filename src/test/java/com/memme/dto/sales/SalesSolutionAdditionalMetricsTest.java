package com.memme.dto.sales;

import java.time.DayOfWeek;
import java.util.List;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSolutionAdditionalMetricsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesOptionalMenuRankingsBusinessHoursAndConditionPatterns() {
        var metrics = metrics(
                List.of(new SalesSolutionMetrics.MenuRanking(1, "아메리카노", 800_000L, 250L)),
                List.of(new SalesSolutionMetrics.BusinessHours(
                        DayOfWeek.MONDAY, false, "09:00", "21:00")),
                List.of(new SalesSolutionMetrics.ConditionPattern(
                        "RAIN", 7, 23,
                        List.of(new SalesSolutionMetrics.ConditionMenu(
                                "아이스 아메리카노", 84, 112, -0.25
                        ))))
        );

        var json = mapper.readTree(mapper.writeValueAsString(metrics));

        assertThat(json.path("menuRankings").get(0).path("netSales").asLong()).isEqualTo(800_000L);
        assertThat(json.path("menuRankings").get(0).path("quantity").asLong()).isEqualTo(250L);
        assertThat(json.path("businessHours").get(0).path("dayOfWeek").asString()).isEqualTo("MONDAY");
        assertThat(json.path("businessHours").get(0).path("openTime").asString()).isEqualTo("09:00");
        assertThat(json.has("businessHoursComplete")).isFalse();
        assertThat(json.path("businessHours").get(0).has("timeConfigurationValid")).isFalse();
        assertThat(json.path("conditionPatterns").get(0).path("sampleDays").asInt()).isEqualTo(7);
        assertThat(json.path("conditionPatterns").get(0).path("menus").get(0).path("vsBaseline").asDouble())
                .isEqualTo(-0.25);
    }

    @Test
    void omitsOptionalMetricsWhenUnavailableAndKeepsLegacyConstructor() {
        var metrics = new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), null, null,
                new SalesSolutionMetrics.WeatherContext(
                        "2026-10-07 11:00", "비", 14, 24, 0.6,
                        List.of(15), null, true
                )
        );

        var json = mapper.readTree(mapper.writeValueAsString(metrics));

        assertThat(json.has("menuRankings")).isFalse();
        assertThat(json.has("businessHours")).isFalse();
        assertThat(json.has("conditionPatterns")).isFalse();
        assertThat(json.has("weatherContext")).isTrue();
    }

    @Test
    void validatesConditionPatternSampleThresholdAndMenuLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var insufficientDays = metrics(
                    null,
                    null,
                    List.of(new SalesSolutionMetrics.ConditionPattern(
                            "RAIN", 4, 5,
                            List.of(new SalesSolutionMetrics.ConditionMenu("아이스 아메리카노", 84, 112, -0.25))
                    ))
            );
            var tooManyMenus = metrics(
                    null,
                    null,
                    List.of(new SalesSolutionMetrics.ConditionPattern(
                            "RAIN", 5, 5,
                            List.of(
                                    new SalesSolutionMetrics.ConditionMenu("메뉴1", 1, 2, -0.5),
                                    new SalesSolutionMetrics.ConditionMenu("메뉴2", 1, 2, -0.5),
                                    new SalesSolutionMetrics.ConditionMenu("메뉴3", 1, 2, -0.5),
                                    new SalesSolutionMetrics.ConditionMenu("메뉴4", 1, 2, -0.5)
                            )
                    ))
            );

            assertThat(validator.validate(insufficientDays))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .contains("sampleDays"));
            assertThat(validator.validate(tooManyMenus))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .contains("menus"));
        }
    }

    @Test
    void rejectsInvalidBusinessHourTimeFormat() {
        var metrics = metrics(
                null,
                List.of(new SalesSolutionMetrics.BusinessHours(DayOfWeek.MONDAY, false, "9:00", "21:00")),
                null
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .contains("openTime"));
        }
    }

    @Test
    void rejectsBusinessHoursWithDuplicateWeekdays() {
        var businessHours = List.of(
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.MONDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.TUESDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.WEDNESDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.THURSDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.FRIDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.SATURDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.MONDAY, false, "09:00", "21:00")
        );
        var metrics = metrics(null, businessHours, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .contains("businessHoursComplete"));
        }
    }

    @Test
    void rejectsOpenBusinessDayWithoutTimes() {
        var metrics = metrics(
                null,
                List.of(new SalesSolutionMetrics.BusinessHours(DayOfWeek.MONDAY, false, null, "21:00")),
                null
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .contains("timeConfigurationValid"));
        }
    }

    @Test
    void rejectsClosedBusinessDayWithTimes() {
        var metrics = metrics(
                null,
                List.of(new SalesSolutionMetrics.BusinessHours(DayOfWeek.MONDAY, true, "09:00", "21:00")),
                null
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .contains("timeConfigurationValid"));
        }
    }

    @Test
    void acceptsCompleteWeeklyBusinessHoursWithClosedDay() {
        var businessHours = List.of(
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.MONDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.TUESDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.WEDNESDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.THURSDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.FRIDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.SATURDAY, false, "09:00", "21:00"),
                new SalesSolutionMetrics.BusinessHours(DayOfWeek.SUNDAY, true, null, null)
        );
        var metrics = metrics(null, businessHours, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(metrics)).isEmpty();
        }
    }

    private SalesSolutionMetrics metrics(
            List<SalesSolutionMetrics.MenuRanking> menuRankings,
            List<SalesSolutionMetrics.BusinessHours> businessHours,
            List<SalesSolutionMetrics.ConditionPattern> conditionPatterns
    ) {
        return new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000_000, null),
                List.of(), List.of(), null, null, null,
                menuRankings, businessHours, conditionPatterns
        );
    }
}
