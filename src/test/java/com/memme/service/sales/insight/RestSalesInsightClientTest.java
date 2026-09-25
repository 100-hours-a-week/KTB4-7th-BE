package com.memme.service.sales.insight;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.memme.dto.sales.SalesInsightMetrics;
import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightStatus;
import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.dto.sales.SalesSummaryMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestSalesInsightClientTest {

    private static final String BASE_URL = "http://ai.test";
    private static final String INSIGHT_URL = BASE_URL + RestSalesInsightClient.PATH;

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendsBearerTokenIdempotencyKeyAndTypedRequest() {
        RestSalesInsightClient client = client("internal-token", ignored -> { });
        server.expect(requestTo(INSIGHT_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer internal-token"))
                .andExpect(header("Idempotency-Key", "sales-insight:34"))
                .andExpect(jsonPath("$.metrics.salesSummary.totalSales").value(7920000))
                .andExpect(jsonPath("$.metrics.salesSummary.menuSales").value(7480000))
                .andRespond(withSuccess(completedResponse(), MediaType.APPLICATION_JSON));

        var response = client.generate(request());

        assertThat(response.status()).isEqualTo(SalesInsightStatus.COMPLETED);
        assertThat(response.data().insights()).containsExactly("9월 총 매출은 7,920,000원입니다.");
        server.verify();
    }

    @Test
    void omitsAuthorizationHeaderWhenTokenIsBlankAndParsesInsufficientData() {
        RestSalesInsightClient client = client(" ", ignored -> { });
        server.expect(requestTo(INSIGHT_URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("""
                        {
                          "message": "AI 인사이트에 필요한 데이터가 부족합니다.",
                          "status": "INSUFFICIENT_DATA",
                          "data": {"missingData": ["SALES_HISTORY"]}
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.generate(request());

        assertThat(response.status()).isEqualTo(SalesInsightStatus.INSUFFICIENT_DATA);
        assertThat(response.data().missingData()).containsExactly("SALES_HISTORY");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void retriesTemporaryHttpFailureOnceAfter500Millis(int statusCode) {
        AtomicLong sleptMillis = new AtomicLong();
        RestSalesInsightClient client = client("internal-token", sleptMillis::set);
        server.expect(requestTo(INSIGHT_URL))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));
        server.expect(requestTo(INSIGHT_URL))
                .andRespond(withSuccess(completedResponse(), MediaType.APPLICATION_JSON));

        var response = client.generate(request());

        assertThat(response.status()).isEqualTo(SalesInsightStatus.COMPLETED);
        assertThat(sleptMillis).hasValue(500L);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 422, 500})
    void doesNotRetryPermanentHttpFailure(int statusCode) {
        AtomicInteger sleepCount = new AtomicInteger();
        RestSalesInsightClient client = client("internal-token", ignored -> sleepCount.incrementAndGet());
        server.expect(requestTo(INSIGHT_URL))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(SalesInsightClientException.class);
        assertThat(sleepCount).hasValue(0);
        server.verify();
    }

    @Test
    void retriesConnectionFailureOnlyOnce() {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicLong sleptMillis = new AtomicLong();
        RestClient.Builder failingBuilder = RestClient.builder()
                .requestFactory((uri, method) -> {
                    requestCount.incrementAndGet();
                    throw new IOException("connection refused");
                });
        RestSalesInsightClient client = new RestSalesInsightClient(
                failingBuilder,
                BASE_URL,
                "internal-token",
                sleptMillis::set
        );

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(SalesInsightClientException.class);
        assertThat(requestCount).hasValue(2);
        assertThat(sleptMillis).hasValue(500L);
    }

    @Test
    void rejectsEmptyResponseBody() {
        RestSalesInsightClient client = client("internal-token", ignored -> { });
        server.expect(requestTo(INSIGHT_URL))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(SalesInsightClientException.class)
                .hasMessage("AI 인사이트 응답 본문이 없습니다.");
        server.verify();
    }

    private RestSalesInsightClient client(
            String bearerToken,
            RestSalesInsightClient.RetrySleeper retrySleeper
    ) {
        return new RestSalesInsightClient(builder, BASE_URL, bearerToken, retrySleeper);
    }

    private String completedResponse() {
        return """
                {
                  "message": "매출 AI 인사이트를 생성했습니다.",
                  "status": "COMPLETED",
                  "data": {
                    "targetMonth": "2026-09",
                    "insights": ["9월 총 매출은 7,920,000원입니다."]
                  }
                }
                """;
    }

    private SalesInsightRequest request() {
        return new SalesInsightRequest(
                1L,
                56L,
                34L,
                YearMonth.of(2026, 9),
                SalesInsightTriggerType.UPLOAD,
                new SalesInsightMetrics(
                        new SalesSummaryMetric(
                                new BigDecimal("7920000"),
                                new BigDecimal("7480000"),
                                923L,
                                new BigDecimal("8581"),
                                new BigDecimal("0.042")
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                3
        );
    }
}
