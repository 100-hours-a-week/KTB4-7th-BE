package com.memme.service.sales.forecast;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.memme.dto.sales.SalesForecastBatchRequest;
import com.memme.dto.sales.SalesForecastBatchResponse;
import com.memme.dto.sales.SalesForecastStatus;
import com.memme.exception.SalesForecastAiException;
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
public class RestSalesForecastAiClient implements SalesForecastAiClient {

    private static final String FORECAST_PATH = "/internal/v1/ai/forecast/batch";
    private static final int EXPECTED_HORIZON_DAYS = 35;
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(502, 503, 504);

    private final RestClient restClient;
    private final String internalAiToken;
    private final Validator validator;
    private final JsonMapper jsonMapper;

    @Autowired
    public RestSalesForecastAiClient(
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

    RestSalesForecastAiClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String internalAiToken,
            Validator validator,
            JsonMapper jsonMapper
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.internalAiToken = internalAiToken == null ? "" : internalAiToken.trim();
        this.validator = validator;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public SalesForecastBatchResponse createForecast(SalesForecastBatchRequest request) {
        validateRequest(request);

        try {
            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri(FORECAST_PATH)
                    .contentType(MediaType.APPLICATION_JSON);
            if (!internalAiToken.isBlank()) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAiToken);
            }

            SalesForecastBatchResponse response = requestSpec
                    .body(request)
                    .retrieve()
                    .body(SalesForecastBatchResponse.class);
            validateResponse(response);
            return response;
        } catch (RestClientResponseException exception) {
            throw convertHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new SalesForecastAiException(
                    0,
                    "AI_CONNECTION_ERROR",
                    "매출 예측 AI 서버에 연결하지 못했습니다.",
                    true,
                    exception
            );
        } catch (RestClientException exception) {
            throw new SalesForecastAiException(
                    0,
                    "INVALID_AI_RESPONSE",
                    "매출 예측 AI 응답 형식이 올바르지 않습니다.",
                    false,
                    exception
            );
        }
    }

    private void validateRequest(SalesForecastBatchRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new SalesForecastAiException(
                    0,
                    "INVALID_FORECAST_REQUEST",
                    "매출 예측 요청값이 올바르지 않습니다.",
                    false
            );
        }
    }

    private void validateResponse(SalesForecastBatchResponse response) {
        if (response == null || !validator.validate(response).isEmpty()) {
            throw invalidResponse();
        }

        if (response.status() == SalesForecastStatus.INSUFFICIENT_DATA) {
            validateInsufficientResponse(response.data());
            return;
        }

        if (response.status() != null && response.status() != SalesForecastStatus.COMPLETED) {
            throw invalidResponse();
        }
        validateCompletedResponse(response.data());
    }

    private void validateInsufficientResponse(SalesForecastBatchResponse.Data data) {
        if (data.missingData() == null
                || !data.missingData().contains("INSUFFICIENT_HISTORY")
                || (data.predictions() != null && !data.predictions().isEmpty())) {
            throw invalidResponse();
        }
    }

    private void validateCompletedResponse(SalesForecastBatchResponse.Data data) {
        List<SalesForecastBatchResponse.Prediction> predictions = data.predictions();
        if (data.forecastStartDate() == null
                || data.forecastEndDate() == null
                || data.horizonDays() == null
                || data.horizonDays() != EXPECTED_HORIZON_DAYS
                || predictions == null
                || predictions.size() != EXPECTED_HORIZON_DAYS
                || !data.forecastEndDate().equals(data.forecastStartDate().plusDays(EXPECTED_HORIZON_DAYS - 1L))) {
            throw invalidResponse();
        }

        for (int index = 0; index < predictions.size(); index++) {
            SalesForecastBatchResponse.Prediction prediction = predictions.get(index);
            LocalDate expectedDate = data.forecastStartDate().plusDays(index);
            if (!expectedDate.equals(prediction.targetDate())
                    || prediction.lowerBound() > prediction.predictedSalesAmount()
                    || prediction.predictedSalesAmount() > prediction.upperBound()) {
                throw invalidResponse();
            }
        }
    }

    private SalesForecastAiException convertHttpError(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        AiErrorResponse errorResponse = readErrorResponse(exception.getResponseBodyAsString());
        String code = errorResponse != null && errorResponse.error() != null
                && errorResponse.error().code() != null
                ? errorResponse.error().code()
                : "AI_HTTP_" + statusCode;
        String message = errorResponse != null && errorResponse.message() != null
                ? errorResponse.message()
                : "매출 예측 AI 호출에 실패했습니다.";
        boolean retryable = errorResponse != null && errorResponse.error() != null
                && errorResponse.error().retryable() != null
                ? errorResponse.error().retryable()
                : RETRYABLE_STATUS_CODES.contains(statusCode);
        return new SalesForecastAiException(statusCode, code, message, retryable, exception);
    }

    private AiErrorResponse readErrorResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(body, AiErrorResponse.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private SalesForecastAiException invalidResponse() {
        return new SalesForecastAiException(
                0,
                "INVALID_AI_RESPONSE",
                "매출 예측 AI 응답 형식이 올바르지 않습니다.",
                false
        );
    }

    private static RestClient.Builder productionBuilder(long connectTimeoutSeconds, long readTimeoutSeconds) {
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("AI timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiErrorResponse(String message, AiError error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiError(String code, Boolean retryable) {}
}
