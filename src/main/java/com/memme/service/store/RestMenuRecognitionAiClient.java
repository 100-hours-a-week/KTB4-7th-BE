package com.memme.service.store;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RestMenuRecognitionAiClient implements MenuRecognitionAiClient {

    private final RestClient client;
    private final String token;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public RestMenuRecognitionAiClient(
            @Value("${AI_BASE_URL:http://localhost:8000}") String baseUrl,
            @Value("${INTERNAL_AI_TOKEN:}") String token,
            @Value("${AI_CONNECT_TIMEOUT_SECONDS:3}") long connectTimeoutSeconds,
            @Value("${AI_READ_TIMEOUT_SECONDS:30}") long readTimeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.token = token == null ? "" : token.trim();
    }

    @Override
    public List<DetectedItem> recognize(long storeId, long batchId, List<Image> images) {
        var request = client.post().uri("/internal/v2/ai/menu-recognition")
                .contentType(MediaType.APPLICATION_JSON);
        if (!token.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RecognitionEnvelope response;
        try {
            response = request.body(new RecognitionRequest(storeId, batchId, images))
                    .retrieve().body(RecognitionEnvelope.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 422) {
                String code = failureCode(exception.getResponseBodyAsString());
                if ("LOW_IMAGE_QUALITY".equals(code) || "MENU_TEXT_NOT_FOUND".equals(code)) {
                    throw new MenuRecognitionFailure(code);
                }
            }
            throw exception;
        }
        if (response == null || response.data() == null || response.data().detectedItems() == null) {
            throw new IllegalStateException("AI 메뉴 인식 응답이 올바르지 않습니다.");
        }
        return response.data().detectedItems();
    }

    private String failureCode(String body) {
        try {
            var json = mapper.readTree(body);
            String errorCode = json.path("error").path("code").asString();
            return errorCode.isBlank() ? json.path("data").path("code").asString() : errorCode;
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private record RecognitionRequest(long storeId, long batchId, List<Image> images) {
    }

    private record RecognitionEnvelope(String message, RecognitionData data) {
    }

    private record RecognitionData(List<DetectedItem> detectedItems) {
    }
}
