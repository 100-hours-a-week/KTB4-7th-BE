package com.memme.service.sales.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.sales.ProfitAiInsightRequest;
import com.memme.dto.sales.ProfitAiInsightResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfitInsightServiceTest {

    @Test
    void AI가_생성한_문장을_조회_응답에_그대로_매핑한다() {
        ProfitAiInsightClient client = mock(ProfitAiInsightClient.class);
        ProfitAiInsightRequest request = request();
        when(client.generate(request)).thenReturn(new ProfitAiInsightResponse(
                "순이익 인사이트를 생성했습니다.", "COMPLETED", new ProfitAiInsightResponse.Data(List.of("예상 순이익은 600,000원이에요."))));

        var result = new ProfitInsightService(client).describe(request);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.insights()).containsExactly("예상 순이익은 600,000원이에요.");
        verify(client).generate(request);
    }

    @Test
    void 데이터_부족은_빈_문장으로_매핑한다() {
        ProfitAiInsightClient client = mock(ProfitAiInsightClient.class);
        ProfitAiInsightRequest request = request();
        when(client.generate(request)).thenReturn(new ProfitAiInsightResponse(
                "순이익 인사이트에 필요한 데이터가 부족합니다.", "INSUFFICIENT_DATA", new ProfitAiInsightResponse.Data(List.of())));

        var result = new ProfitInsightService(client).describe(request);

        assertThat(result.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.insights()).isEmpty();
    }

    private ProfitAiInsightRequest request() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        return new ProfitAiInsightRequest(20L,
                new ProfitAiInsightRequest.Period(date, date),
                new ProfitAiInsightRequest.Summary(1_000_000, 300_000, 100_000,
                        600_000, new BigDecimal("0.6"), 500_000L, new BigDecimal("0.2")),
                List.of(new com.memme.dto.sales.ProfitAnalysisResponse.DailyProfit(date, 600_000)), 3);
    }
}
