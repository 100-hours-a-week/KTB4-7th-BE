package com.memme.service.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

import com.memme.dto.chat.ChatAiRequest;
import com.memme.exception.chat.ChatAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RestChatAiClient implements ChatAiClient {

    private static final String CHAT_PATH = "/internal/v1/ai/chat/messages";

    private final HttpClient httpClient;
    private final URI chatUri;
    private final String internalAiToken;
    private final Duration readTimeout;
    private final ObjectMapper objectMapper;

    public RestChatAiClient(
            @Value("${AI_BASE_URL:http://localhost:8000}") String baseUrl,
            @Value("${INTERNAL_AI_TOKEN:}") String internalAiToken,
            @Value("${AI_CONNECT_TIMEOUT_SECONDS:3}") long connectTimeoutSeconds,
            @Value("${AI_READ_TIMEOUT_SECONDS:30}") long readTimeoutSeconds,
            ObjectMapper objectMapper
    ) {
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("AI timeout must be positive");
        }
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.chatUri = URI.create(baseUrl + CHAT_PATH);
        this.internalAiToken = internalAiToken == null ? "" : internalAiToken.trim();
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
        this.objectMapper = objectMapper;
    }

    @Override
    public void stream(ChatAiRequest request, Consumer<ChatAiEvent> eventConsumer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(chatUri)
                .timeout(readTimeout)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
        if (!internalAiToken.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAiToken);
        }

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            if (response.statusCode() != 200) {
                throw new ChatAiException("AI 챗봇 호출에 실패했습니다: " + response.statusCode());
            }
            readEvents(response, eventConsumer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatAiException("AI 챗봇 호출이 중단됐습니다.", exception);
        } catch (IOException exception) {
            throw new ChatAiException("AI 챗봇 서버에 연결하지 못했습니다.", exception);
        }
    }

    private void readEvents(
            HttpResponse<java.io.InputStream> response,
            Consumer<ChatAiEvent> eventConsumer
    ) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                response.body(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    return;
                }
                eventConsumer.accept(parseEvent(data));
            }
        }
    }

    private ChatAiEvent parseEvent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode eventData = root.path("data");
            if ("error".equals(root.path("event").asText())) {
                return ChatAiEvent.error(
                        eventData.path("code").asText("AI_GENERATION_ERROR"),
                        eventData.path("message").asText("답변 생성에 실패했습니다.")
                );
            }
            JsonNode evidence = eventData.get("evidence");
            return ChatAiEvent.chunk(
                    eventData.path("content").asText(),
                    evidence == null || evidence.isNull() ? null : evidence.toString()
            );
        } catch (RuntimeException exception) {
            throw new ChatAiException("AI 챗봇 응답 형식이 올바르지 않습니다.", exception);
        }
    }
}
