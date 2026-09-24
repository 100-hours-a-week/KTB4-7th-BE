package com.memme.service.sales.insight;

import java.net.http.HttpClient;
import java.time.Duration;

import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestSalesInsightClient implements SalesInsightClient {

    static final String PATH = "/internal/v1/ai/sales-insights";
    private static final int MAX_RETRY_COUNT = 1;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private final RestClient restClient;
    private final String bearerToken;
    private final RetrySleeper retrySleeper;

    @Autowired
    public RestSalesInsightClient(
            @Value("${AI_BASE_URL:http://localhost:8000}") String baseUrl,
            @Value("${INTERNAL_AI_TOKEN:}") String bearerToken,
            @Value("${AI_CONNECT_TIMEOUT_SECONDS:3}") long connectTimeoutSeconds,
            @Value("${AI_READ_TIMEOUT_SECONDS:30}") long readTimeoutSeconds
    ) {
        this(
                createRestClient(baseUrl, connectTimeoutSeconds, readTimeoutSeconds),
                bearerToken,
                Thread::sleep
        );
    }

    RestSalesInsightClient(
            RestClient.Builder builder,
            String baseUrl,
            String bearerToken,
            RetrySleeper retrySleeper
    ) {
        this(builder.baseUrl(baseUrl).build(), bearerToken, retrySleeper);
    }

    private RestSalesInsightClient(
            RestClient restClient,
            String bearerToken,
            RetrySleeper retrySleeper
    ) {
        this.restClient = restClient;
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.retrySleeper = retrySleeper;
    }

    @Override
    public SalesInsightResponse generate(SalesInsightRequest request) {
        int retryCount = 0;
        while (true) {
            try {
                SalesInsightResponse response = request(request);
                if (response == null) {
                    throw new SalesInsightClientException("AI 인사이트 응답 본문이 없습니다.");
                }
                return response;
            } catch (RestClientException exception) {
                if (retryCount >= MAX_RETRY_COUNT || !isRetryable(exception)) {
                    throw new SalesInsightClientException("AI 인사이트 생성 요청에 실패했습니다.", exception);
                }
                retryCount++;
                sleepBeforeRetry();
            }
        }
    }

    private SalesInsightResponse request(SalesInsightRequest request) {
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sales-insight:" + request.analysisRunId());
        if (!bearerToken.isBlank()) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return spec.body(request)
                .retrieve()
                .body(SalesInsightResponse.class);
    }

    private boolean isRetryable(RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 502 || status == 503 || status == 504;
        }
        return false;
    }

    private void sleepBeforeRetry() {
        try {
            retrySleeper.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SalesInsightClientException("AI 인사이트 재시도 대기가 중단되었습니다.", exception);
        }
    }

    private static RestClient createRestClient(
            String baseUrl,
            long connectTimeoutSeconds,
            long readTimeoutSeconds
    ) {
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("AI timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
