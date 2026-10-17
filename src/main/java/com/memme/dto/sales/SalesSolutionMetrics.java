package com.memme.dto.sales;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid List<MenuRanking> menuRankings,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Size(min = 7, max = 7) @Valid List<BusinessHours> businessHours,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid List<ConditionPattern> conditionPatterns
) {

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
        businessHours = copyOfNullable(businessHours);
        conditionPatterns = copyOfNullable(conditionPatterns);
    }

    private static <T> List<T> copyOfNullable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    @JsonIgnore
    @AssertTrue
    public boolean isBusinessHoursComplete() {
        return businessHours == null || businessHours.stream()
                .map(BusinessHours::dayOfWeek)
                .filter(Objects::nonNull)
                .distinct()
                .count() == 7;
    }

    public record SalesSummary(long netSales, Double vsPrevPeriod) {}

    public record HourlyPoint(
            @NotBlank String dayType,
            @Min(0) @Max(23) int hour,
            long amount
    ) {}

    public record CategoryPoint(
            @NotBlank String name,
            double share,
            Double vsPrevPeriod
    ) {}

    public record WeatherContext(
            @NotBlank String basisDateTime,
            @NotBlank String skyText,
            int minTempC,
            int maxTempC,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double maxPop,
            @NotNull List<@NotNull @Min(0) @Max(23) Integer> rainHours,
            @JsonInclude(JsonInclude.Include.NON_NULL) @DecimalMin("0.0") Double precipitationMm,
            @NotNull Boolean isNotable
    ) {}

    public record MenuRanking(
            @Positive int rank,
            @NotBlank String menuName,
            @NotNull @PositiveOrZero Long netSales,
            @NotNull @PositiveOrZero Long quantity
    ) {}

    public record BusinessHours(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull Boolean isClosed,
            @Pattern(regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d$") String openTime,
            @Pattern(regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d$") String closeTime
    ) {
        @JsonIgnore
        @AssertTrue
        public boolean isTimeConfigurationValid() {
            if (isClosed == null) {
                return true;
            }
            if (isClosed) {
                return openTime == null && closeTime == null;
            }
            return openTime != null && closeTime != null;
        }
    }

    public record ConditionPattern(
            @NotBlank String condition,
            @Min(5) int sampleDays,
            @Min(5) int baselineDays,
            @NotEmpty @Size(max = 3) @Valid List<ConditionMenu> menus
    ) {}

    public record ConditionMenu(
            @NotBlank String menuName,
            @Min(0) int avgQuantity,
            @Positive int baselineAvgQuantity,
            @NotNull @DecimalMin("-1.0") Double vsBaseline
    ) {}
}
