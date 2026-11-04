package com.memme.dto.sales;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SalesSolutionMetrics(
        @NotNull @Valid SalesSummary salesSummary,
        @NotNull @Valid List<HourlyPoint> hourlyProfile,
        @NotNull @Valid List<CategoryPoint> categoryBreakdown,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long predictedSalesToday,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> reviewSummary,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid WeatherContext weatherContext,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Size(max = 10) @Valid List<MenuRankingMetric> menuRankings,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid BusinessHours businessHours,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid List<ConditionPattern> conditionPatterns,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid DistrictBenchmarkMetric districtBenchmark
) {

    public SalesSolutionMetrics(
            SalesSummary salesSummary,
            List<HourlyPoint> hourlyProfile,
            List<CategoryPoint> categoryBreakdown,
            Long predictedSalesToday,
            Map<String, Object> reviewSummary,
            WeatherContext weatherContext,
            List<MenuRankingMetric> menuRankings,
            BusinessHours businessHours,
            List<ConditionPattern> conditionPatterns
    ) {
        this(salesSummary, hourlyProfile, categoryBreakdown, predictedSalesToday, reviewSummary,
                weatherContext, menuRankings, businessHours, conditionPatterns, null);
    }

    public SalesSolutionMetrics(
            SalesSummary salesSummary,
            List<HourlyPoint> hourlyProfile,
            List<CategoryPoint> categoryBreakdown,
            Long predictedSalesToday,
            Map<String, Object> reviewSummary
    ) {
        this(salesSummary, hourlyProfile, categoryBreakdown, predictedSalesToday, reviewSummary, null, null, null, null);
    }

    public SalesSolutionMetrics(
            SalesSummary salesSummary,
            List<HourlyPoint> hourlyProfile,
            List<CategoryPoint> categoryBreakdown,
            Long predictedSalesToday,
            Map<String, Object> reviewSummary,
            WeatherContext weatherContext
    ) {
        this(salesSummary, hourlyProfile, categoryBreakdown, predictedSalesToday, reviewSummary,
                weatherContext, null, null, null);
    }

    public SalesSolutionMetrics {
        menuRankings = copyOfNullable(menuRankings);
        conditionPatterns = copyOfNullable(conditionPatterns);
    }

    private static <T> List<T> copyOfNullable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    public record SalesSummary(
            long netSales,
            Double vsPrevPeriod,
            @JsonInclude(JsonInclude.Include.NON_NULL) String period
    ) {
        public SalesSummary(long netSales, Double vsPrevPeriod) {
            this(netSales, vsPrevPeriod, null);
        }
    }

    public record HourlyPoint(
            @NotBlank String dayType,
            @Min(0) @Max(23) int hour,
            long amount
    ) {}

    public record CategoryPoint(
            @NotBlank String name,
            double share,
            Double vsPrevPeriod,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double previousShare
    ) {
        public CategoryPoint(String name, double share, Double vsPrevPeriod) {
            this(name, share, vsPrevPeriod, null);
        }
    }

    public record WeatherContext(
            @NotBlank String basisDateTime,
            @NotBlank String skyText,
            int minTempC,
            int maxTempC,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double maxPop,
            @NotNull List<@NotNull @Min(0) @Max(23) Integer> rainHours,
            @JsonInclude(JsonInclude.Include.NON_NULL) @DecimalMin("0.0") Double precipitationMm,
            @NotNull Boolean isNotable,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<@NotBlank String> alerts
    ) {
        public WeatherContext(
                String basisDateTime,
                String skyText,
                int minTempC,
                int maxTempC,
                Double maxPop,
                List<Integer> rainHours,
                Double precipitationMm,
                Boolean isNotable
        ) {
            this(basisDateTime, skyText, minTempC, maxTempC, maxPop, rainHours, precipitationMm, isNotable, null);
        }

        public WeatherContext {
            alerts = alerts == null ? null : List.copyOf(new LinkedHashSet<>(alerts));
        }
    }

    public record BusinessHours(
            @JsonProperty("WEEKDAY") @NotNull @Valid BusinessHourBand weekday,
            @JsonProperty("WEEKEND") @NotNull @Valid BusinessHourBand weekend,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            List<@Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN") String> closedDaysOfWeek
    ) {
        public BusinessHours {
            closedDaysOfWeek = copyOfNullable(closedDaysOfWeek);
        }
    }

    public record BusinessHourBand(
            @NotBlank @Pattern(regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d$") String opensAt,
            @NotBlank @Pattern(regexp = "^(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)$") String closesAt
    ) {}

    public record ConditionPattern(
            @NotBlank String condition,
            @Positive int basisWeeks,
            @DecimalMin("0.0") double thresholdMm,
            @Min(5) int sampleDays,
            @Min(5) int baselineDays,
            @DecimalMin("0.0") @DecimalMax("1.0") double sampleWeekdayRatio,
            @DecimalMin("0.0") @DecimalMax("1.0") double baselineWeekdayRatio,
            @NotNull @Size(max = 3) @Valid List<ConditionMenu> menus,
            @PositiveOrZero long avgNetSales,
            @PositiveOrZero long baselineAvgNetSales
    ) {
        public ConditionPattern(
                String condition, int basisWeeks, double thresholdMm, int sampleDays, int baselineDays,
                double sampleWeekdayRatio, double baselineWeekdayRatio, List<ConditionMenu> menus
        ) {
            this(condition, basisWeeks, thresholdMm, sampleDays, baselineDays,
                    sampleWeekdayRatio, baselineWeekdayRatio, menus, 0, 0);
        }
    }

    public record ConditionMenu(
            @NotBlank String menuName,
            @Min(0) int avgQuantity,
            @Positive int baselineAvgQuantity,
            @NotNull @DecimalMin("-1.0") Double vsBaseline,
            @PositiveOrZero long avgSales,
            @PositiveOrZero long baselineAvgSales
    ) {}
}
