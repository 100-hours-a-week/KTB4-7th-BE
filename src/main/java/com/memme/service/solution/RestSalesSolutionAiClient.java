package com.memme.service.solution;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.dto.sales.SalesSolutionGenerationStatus;
import com.memme.exception.solution.SalesSolutionAiException;
import jakarta.validation.Validator;
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
import tools.jackson.databind.json.JsonMapper;

@Component
public class RestSalesSolutionAiClient implements SalesSolutionAiClient {

    private static final String GENERATION_PATH = "/internal/v1/ai/solutions/generate";
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(502, 503, 504);

    private final RestClient restClient;
    private final String internalAiToken;
    private final Validator validator;
    private final JsonMapper jsonMapper;

    @Autowired
    public RestSalesSolutionAiClient(
            @Value("${AI_BASE_URL:http://localhost:8000}") String baseUrl,
            @Value("${INTERNAL_AI_TOKEN:}") String internalAiToken,
            @Value("${AI_CONNECT_TIMEOUT_SECONDS:3}") long connectTimeoutSeconds,
            @Value("${AI_READ_TIMEOUT_SECONDS:30}") long readTimeoutSeconds,
            Validator validator
    ) {
        this(
                productionBuilder(connectTimeoutSeconds, readTimeoutSeconds),
                baseUrl,
                internalAiToken,
                validator,
                JsonMapper.builder().build()
        );
    }

    RestSalesSolutionAiClient(
            RestClient.Builder builder,
            String baseUrl,
            String internalAiToken,
            Validator validator,
            JsonMapper jsonMapper
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalAiToken = internalAiToken == null ? "" : internalAiToken.trim();
        this.validator = validator;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public SalesSolutionGenerationResponse generate(SalesSolutionGenerationRequest request) {
        validateRequest(request);
        try {
            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri(GENERATION_PATH)
                    .contentType(MediaType.APPLICATION_JSON);
            if (!internalAiToken.isBlank()) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAiToken);
            }
            SalesSolutionGenerationResponse response = requestSpec
                    .body(request)
                    .retrieve()
                    .body(SalesSolutionGenerationResponse.class);
            validateResponse(response);
            return response;
        } catch (RestClientResponseException exception) {
            throw convertHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new SalesSolutionAiException(
                    0,
                    "AI_CONNECTION_ERROR",
                    "솔루션 AI 서버에 연결하지 못했습니다.",
                    true,
                    exception
            );
        } catch (RestClientException exception) {
            throw new SalesSolutionAiException(
                    0,
                    "INVALID_AI_RESPONSE",
                    "솔루션 AI 응답 형식이 올바르지 않습니다.",
                    false,
                    exception
            );
        }
    }

    private void validateRequest(SalesSolutionGenerationRequest request) {
        if (request == null
                || !validator.validate(request).isEmpty()
                || ("UPLOAD".equals(request.triggerType()) && request.salesAnalysisId() == null)) {
            throw new SalesSolutionAiException(
                    0,
                    "INVALID_SOLUTION_REQUEST",
                    "솔루션 생성 요청값이 올바르지 않습니다.",
                    false
            );
        }
    }

    private void validateResponse(SalesSolutionGenerationResponse response) {
        if (response == null || !validator.validate(response).isEmpty()) {
            throw invalidResponse();
        }
        if (response.data() != null
                && (response.status() == null
                || response.status() == SalesSolutionGenerationStatus.COMPLETED)) {
            validateCompleted(response);
            return;
        }
        if (response.status() == SalesSolutionGenerationStatus.INSUFFICIENT_DATA) {
            if (response.data() != null && response.data().solutionCards() != null
                    && !response.data().solutionCards().isEmpty()) {
                throw invalidResponse();
            }
            return;
        }
        if (response.error() == null || response.error().code() == null
                || response.error().code().isBlank()) {
            throw invalidResponse();
        }
    }

    private void validateCompleted(SalesSolutionGenerationResponse response) {
        if (response.data() == null || response.error() != null
                || response.data().targetDate() == null
                || response.data().modelVersion() == null
                || response.data().modelVersion().isBlank()) {
            throw invalidResponse();
        }
        List<SalesSolutionGenerationResponse.SolutionCard> cards = response.data().solutionCards();
        if (cards == null || cards.isEmpty() || cards.size() > 3) {
            throw invalidResponse();
        }
        Set<Integer> ranks = new HashSet<>();
        for (SalesSolutionGenerationResponse.SolutionCard card : cards) {
            if (card == null || !validator.validate(card).isEmpty() || !ranks.add(card.rankNo())) {
                throw invalidResponse();
            }
        }
    }

    private SalesSolutionAiException convertHttpError(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        SalesSolutionGenerationResponse response = readErrorResponse(exception.getResponseBodyAsString());
        String code = response != null && response.error() != null
                && response.error().code() != null
                ? response.error().code()
                : "AI_HTTP_" + statusCode;
        String message = response != null && response.message() != null
                ? response.message()
                : "솔루션 AI 호출에 실패했습니다.";
        boolean retryable = response != null && response.error() != null
                && response.error().retryable() != null
                ? response.error().retryable()
                : RETRYABLE_STATUS_CODES.contains(statusCode);
        return new SalesSolutionAiException(statusCode, code, message, retryable, exception);
    }

    private SalesSolutionGenerationResponse readErrorResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(body, SalesSolutionGenerationResponse.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private SalesSolutionAiException invalidResponse() {
        return new SalesSolutionAiException(
                0,
                "INVALID_AI_RESPONSE",
                "솔루션 AI 응답 형식이 올바르지 않습니다.",
                false
        );
    }

    private static RestClient.Builder productionBuilder(long connectSeconds, long readSeconds) {
        if (connectSeconds <= 0 || readSeconds <= 0) {
            throw new IllegalArgumentException("AI timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readSeconds));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
