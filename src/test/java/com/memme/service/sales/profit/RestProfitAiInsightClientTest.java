package com.memme.service.sales.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.memme.dto.sales.ProfitAiInsightRequest;
import com.memme.dto.sales.ProfitAnalysisResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestProfitAiInsightClientTest {

    private static final String BASE_URL = "http://ai.test";
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void 계산_완료_지표만_계약대로_전송하고_AI_문장을_받는다() {
        server.expect(requestTo(BASE_URL + RestProfitAiInsightClient.PATH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer internal-token"))
                .andExpect(jsonPath("$.storeId").value(20))
                .andExpect(jsonPath("$.period.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.summary.netProfit").value(600000))
                .andExpect(jsonPath("$.summary.netProfitChangeRate").value(0.2))
                .andExpect(jsonPath("$.dailyProfits[0].netProfit").value(600000))
                .andExpect(jsonPath("$.monthlyCostItems").doesNotExist())
                .andRespond(withSuccess("""
                        {"message":"순이익 인사이트를 생성했습니다.","status":"COMPLETED","data":{"insights":["예상 순이익은 600,000원이에요."]}}
                        """, MediaType.APPLICATION_JSON));

        var response = client().generate(request());

        assertThat(response.message()).isEqualTo("순이익 인사이트를 생성했습니다.");
        assertThat(response.data().insights()).containsExactly("예상 순이익은 600,000원이에요.");
        server.verify();
    }

    @Test
    void 데이터_부족_응답을_허용한다() {
        server.expect(requestTo(BASE_URL + RestProfitAiInsightClient.PATH))
                .andRespond(withSuccess("""
                        {"message":"순이익 인사이트에 필요한 데이터가 부족합니다.","status":"INSUFFICIENT_DATA","data":{"insights":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client().generate(request()).status()).isEqualTo("INSUFFICIENT_DATA");
        server.verify();
    }

    @Test
    void 비어_있거나_계약을_벗어난_응답을_거부한다() {
        server.expect(requestTo(BASE_URL + RestProfitAiInsightClient.PATH))
                .andRespond(withSuccess("""
                        {"message":"순이익 인사이트를 생성했습니다.","status":"COMPLETED","data":{"insights":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().generate(request()))
                .isInstanceOf(ProfitAiInsightClientException.class);
        server.verify();
    }

    @Test
    void message가_없는_응답을_거부한다() {
        server.expect(requestTo(BASE_URL + RestProfitAiInsightClient.PATH))
                .andRespond(withSuccess("""
                        {"status":"COMPLETED","data":{"insights":["예상 순이익이 증가했어요."]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().generate(request()))
                .isInstanceOf(ProfitAiInsightClientException.class);
        server.verify();
    }

    @Test
    void 데이터_부족_상태에서_문장이_있으면_거부한다() {
        server.expect(requestTo(BASE_URL + RestProfitAiInsightClient.PATH))
                .andRespond(withSuccess("""
                        {"message":"순이익 인사이트에 필요한 데이터가 부족합니다.","status":"INSUFFICIENT_DATA","data":{"insights":["근거 없는 문장"]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().generate(request()))
                .isInstanceOf(ProfitAiInsightClientException.class);
        server.verify();
    }

    @Test
    void HTTP_실패를_호출_오류로_변환한다() {
        server.expect(requestTo(BASE_URL + RestProfitAiInsightClient.PATH))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client().generate(request()))
                .isInstanceOf(ProfitAiInsightClientException.class);
        server.verify();
    }

    @Test
    void 연결_실패를_호출_오류로_변환한다() {
        RestClient.Builder failingBuilder = RestClient.builder()
                .requestFactory((uri, method) -> { throw new IOException("connection refused"); });
        RestProfitAiInsightClient client = new RestProfitAiInsightClient(
                failingBuilder, BASE_URL, "internal-token");

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(ProfitAiInsightClientException.class);
    }

    private RestProfitAiInsightClient client() {
        return new RestProfitAiInsightClient(builder, BASE_URL, "internal-token");
    }

    private ProfitAiInsightRequest request() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        return new ProfitAiInsightRequest(20L, new ProfitAiInsightRequest.Period(date, date),
                new ProfitAiInsightRequest.Summary(1_000_000, 300_000, 100_000, 600_000,
                        new BigDecimal("0.6"), 500_000L, new BigDecimal("0.2")),
                List.of(new ProfitAnalysisResponse.DailyProfit(date, 600_000)), 3);
    }
}
