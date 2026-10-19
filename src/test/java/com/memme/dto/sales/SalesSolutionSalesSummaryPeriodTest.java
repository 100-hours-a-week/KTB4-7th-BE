package com.memme.dto.sales;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSolutionSalesSummaryPeriodTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesPeriodWhenProvided() {
        var summary = new SalesSolutionMetrics.SalesSummary(
                1_183_600L, -0.12, "2026-01-01~2026-03-31");

        var json = mapper.readTree(mapper.writeValueAsString(summary));

        assertThat(json.path("netSales").asLong()).isEqualTo(1_183_600L);
        assertThat(json.path("vsPrevPeriod").asDouble()).isEqualTo(-0.12);
        assertThat(json.path("period").asString()).isEqualTo("2026-01-01~2026-03-31");
    }

    @Test
    void legacyConstructorOmitsUnavailablePeriod() {
        var summary = new SalesSolutionMetrics.SalesSummary(1_183_600L, null);

        var json = mapper.readTree(mapper.writeValueAsString(summary));

        assertThat(json.path("netSales").asLong()).isEqualTo(1_183_600L);
        assertThat(json.has("period")).isFalse();
    }
}
