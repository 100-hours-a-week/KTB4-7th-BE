package com.memme.service.store.recognition;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RestMenuRecognitionAiClient implements MenuRecognitionAiClient {

    static final String PATH = "/internal/v2/ai/menu-recognition";
    private static final String PROCESSING_ERROR = "AI_PROCESSING_ERROR";

    private final RestClient restClient;
    private final String bearerToken;
    private final JsonMapper jsonMapper;

    @Autowired
    public RestMenuRecognitionAiClient(
            @Value("${AI_BASE_URL:http://localhost:8000}") String baseUrl,
            @Value("${INTERNAL_AI_TOKEN:}") String bearerToken,
            @Value("${MENU_AI_CONNECT_TIMEOUT_SECONDS:3}") long connectTimeoutSeconds,
            @Value("${MENU_AI_READ_TIMEOUT_SECONDS:120}") long readTimeoutSeconds) {
        this(createBuilder(connectTimeoutSeconds, readTimeoutSeconds), baseUrl, bearerToken);
    }

    RestMenuRecognitionAiClient(RestClient.Builder builder, String baseUrl, String bearerToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.jsonMapper = JsonMapper.builder().build();
    }

    @Override
    public MenuRecognitionResponse recognize(MenuRecognitionRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON);
            if (!bearerToken.isBlank()) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            MenuRecognitionResponse response = spec.body(request)
                    .retrieve()
                    .body(MenuRecognitionResponse.class);
            validateResponse(request, response);
            return response;
        } catch (RestClientResponseException exception) {
            throw new MenuRecognitionAiException(failReason(exception),
                    "메뉴 인식 AI 요청에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            throw new MenuRecognitionAiException(PROCESSING_ERROR,
                    "메뉴 인식 AI 서버에 연결하지 못했습니다.", exception);
        }
    }

    private void validateResponse(MenuRecognitionRequest request, MenuRecognitionResponse response) {
        if (response == null || response.data() == null || response.data().detectedItems() == null) {
            throw invalidResponse();
        }
        Set<Long> imageIds = request.images().stream()
                .map(MenuRecognitionRequest.Image::imageId)
                .collect(Collectors.toSet());
        Set<Long> failedImageIds = new HashSet<>();
        for (MenuRecognitionResponse.FailedImage failed : response.data().failedImages()) {
            if (failed == null || failed.sourceImageId() == null
                    || !imageIds.contains(failed.sourceImageId())
                    || !failedImageIds.add(failed.sourceImageId())
                    || !isImageFailReason(failed.failReason())) {
                throw invalidResponse();
            }
        }
        if (failedImageIds.size() == imageIds.size()) {
            throw invalidResponse();
        }
        for (MenuRecognitionResponse.DetectedItem item : response.data().detectedItems()) {
            if (item == null || item.sourceImageId() == null
                    || !imageIds.contains(item.sourceImageId())
                    || failedImageIds.contains(item.sourceImageId())
                    || item.name() == null || item.name().isBlank() || item.name().length() > 100
                    || (item.category() != null && item.category().length() > 50)
                    || (item.confidence() != null && (item.confidence().signum() < 0
                    || item.confidence().compareTo(java.math.BigDecimal.ONE) > 0))) {
                throw invalidResponse();
            }
        }
    }

    private boolean isImageFailReason(String reason) {
        return "LOW_IMAGE_QUALITY".equals(reason) || "MENU_TEXT_NOT_FOUND".equals(reason)
                || PROCESSING_ERROR.equals(reason);
    }

    private MenuRecognitionAiException invalidResponse() {
        return new MenuRecognitionAiException(PROCESSING_ERROR, "메뉴 인식 AI 응답이 올바르지 않습니다.");
    }

    private String failReason(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 422) {
            return PROCESSING_ERROR;
        }
        try {
            var body = jsonMapper.readTree(exception.getResponseBodyAsString());
            String code = body.path("failReason").asString();
            if (code.isBlank()) {
                code = body.path("code").asString();
            }
            if (code.isBlank()) {
                code = body.path("data").path("code").asString();
            }
            return "LOW_IMAGE_QUALITY".equals(code) || "MENU_TEXT_NOT_FOUND".equals(code)
                    ? code : PROCESSING_ERROR;
        } catch (RuntimeException ignored) {
            return PROCESSING_ERROR;
        }
    }

    private static RestClient.Builder createBuilder(long connectSeconds, long readSeconds) {
        if (connectSeconds <= 0 || readSeconds <= 0) {
            throw new IllegalArgumentException("menu AI timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        return RestClient.builder().requestFactory(factory);
    }
}
