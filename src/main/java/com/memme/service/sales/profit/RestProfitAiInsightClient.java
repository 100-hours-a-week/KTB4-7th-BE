package com.memme.service.sales.profit;

import com.memme.dto.sales.ProfitAiInsightRequest;
import com.memme.dto.sales.ProfitAiInsightResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestProfitAiInsightClient implements ProfitAiInsightClient {

    static final String PATH = "/internal/v2/ai/profit-insights";

    private final RestClient restClient;
    private final String bearerToken;

    @Autowired
    public RestProfitAiInsightClient(
            @Value("${AI_BASE_URL:http://localhost:8000}") String baseUrl,
            @Value("${INTERNAL_AI_TOKEN:}") String bearerToken,
            @Value("${AI_CONNECT_TIMEOUT_SECONDS:3}") long connectTimeoutSeconds,
            @Value("${AI_READ_TIMEOUT_SECONDS:30}") long readTimeoutSeconds
    ) {
        this(createRestClient(baseUrl, connectTimeoutSeconds, readTimeoutSeconds), bearerToken);
    }

    RestProfitAiInsightClient(RestClient.Builder builder, String baseUrl, String bearerToken) {
        this(builder.baseUrl(baseUrl).build(), bearerToken);
    }

    private RestProfitAiInsightClient(RestClient restClient, String bearerToken) {
        this.restClient = restClient;
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    @Override
    public ProfitAiInsightResponse generate(ProfitAiInsightRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON);
            if (!bearerToken.isBlank()) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            ProfitAiInsightResponse response = spec.body(request)
                    .retrieve()
                    .body(ProfitAiInsightResponse.class);
            validate(response, request.maxInsightCount());
            return response;
        } catch (RestClientException exception) {
            throw new ProfitAiInsightClientException("순이익 AI 인사이트 호출에 실패했습니다.", exception);
        }
    }

    private void validate(ProfitAiInsightResponse response, int maxInsightCount) {
        if (response == null || response.data() == null || response.data().insights() == null) {
            throw new ProfitAiInsightClientException("순이익 AI 인사이트 응답 형식이 올바르지 않습니다.");
        }
        List<String> insights = response.data().insights();
        if ("INSUFFICIENT_DATA".equals(response.status()) && insights.isEmpty()) {
            return;
        }
        if (!"COMPLETED".equals(response.status()) || insights.isEmpty()
                || insights.size() > maxInsightCount
                || insights.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 100)) {
            throw new ProfitAiInsightClientException("순이익 AI 인사이트 응답 형식이 올바르지 않습니다.");
        }
    }

    private static RestClient createRestClient(String baseUrl, long connectTimeoutSeconds,
            long readTimeoutSeconds) {
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("AI timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
