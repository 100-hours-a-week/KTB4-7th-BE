package com.memme.dto.sales;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSolutionDailyAverageMetricsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesDailyAverageNetSalesAndSellingDaysAsIntegersUnderSalesSummary() {
        var metrics = new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(
                        124_946_029L, null, "2026-08-01~2026-10-20", 1_505_373L, 83),
                List.of(), List.of(), null, null);

        var json = mapper.readTree(mapper.writeValueAsString(metrics));
        var summary = json.path("salesSummary");

        assertThat(summary.path("netSales").asLong()).isEqualTo(124_946_029L);
        assertThat(summary.path("dailyAvgNetSales").isIntegralNumber()).isTrue();
        assertThat(summary.path("dailyAvgNetSales").asLong()).isEqualTo(1_505_373L);
        assertThat(summary.path("sellingDays").isIntegralNumber()).isTrue();
        assertThat(summary.path("sellingDays").asInt()).isEqualTo(83);
    }

    @Test
    void omitsUnavailableDailyAverageFieldsAndKeepsLegacyConstructors() {
        var metrics = new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_000L, null, "2026-10-01~2026-10-03"),
                List.of(), List.of(), null, null);

        var json = mapper.readTree(mapper.writeValueAsString(metrics));
        var summary = json.path("salesSummary");

        assertThat(summary.has("dailyAvgNetSales")).isFalse();
        assertThat(summary.has("sellingDays")).isFalse();
    }

    @Test
    void serializesZeroDailyAverageValuesWithoutOmittingThem() {
        var summary = new SalesSolutionMetrics.SalesSummary(0L, null, "2026-10-01~2026-10-03", 0L, 0);

        var json = mapper.readTree(mapper.writeValueAsString(summary));

        assertThat(json.path("dailyAvgNetSales").isIntegralNumber()).isTrue();
        assertThat(json.path("dailyAvgNetSales").asLong()).isZero();
        assertThat(json.path("sellingDays").isIntegralNumber()).isTrue();
        assertThat(json.path("sellingDays").asInt()).isZero();
    }
}
