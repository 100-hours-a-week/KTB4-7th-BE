package com.memme.service.sales.profit;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.dto.sales.ProfitAnalysisResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfitInsightServiceTest {

    private final ProfitInsightService service = new ProfitInsightService();

    @Test
    void 계산된_금액으로만_순이익_비교와_최고_일자를_설명한다() {
        var insight = service.describe(599, 299L, List.of(
                new ProfitAnalysisResponse.DailyProfit(LocalDate.of(2026, 10, 7), 300),
                new ProfitAnalysisResponse.DailyProfit(LocalDate.of(2026, 10, 8), 299)));

        assertThat(insight.status()).isEqualTo("COMPLETED");
        assertThat(insight.insights()).containsExactly(
                "선택 기간의 예상 순이익은 599원이에요.",
                "이전 기간보다 순이익이 300원 늘었어요.",
                "10월 7일의 예상 순이익이 가장 높아요.");
    }

    @Test
    void 전기_비교가_불가능하면_추측하지_않고_순손실만_설명한다() {
        var insight = service.describe(-100, null, List.of(
                new ProfitAnalysisResponse.DailyProfit(LocalDate.of(2026, 10, 7), -100)));

        assertThat(insight.insights()).containsExactly("선택 기간의 예상 순손실은 100원이에요.");
    }
}
