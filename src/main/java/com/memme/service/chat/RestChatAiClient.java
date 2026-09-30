package com.memme.service.chat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.memme.dto.chat.ChatAiRequest;
import com.memme.exception.chat.ChatAiException;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.HttpProtocol;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RestChatAiClient implements ChatAiClient {

    private static final String CHAT_PATH = "/internal/v1/ai/chat/messages";
    private static final Logger log = LoggerFactory.getLogger(RestChatAiClient.class);

    private final WebClient webClient;
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
        this.chatUri = URI.create(baseUrl + CHAT_PATH);
        this.internalAiToken = internalAiToken == null ? "" : internalAiToken.trim();
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .protocol(HttpProtocol.HTTP11)
                                .option(
                                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                        Math.toIntExact(Duration.ofSeconds(connectTimeoutSeconds).toMillis())
                                )
                                .responseTimeout(readTimeout)
                ))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void stream(ChatAiRequest request, Consumer<ChatAiEvent> eventConsumer) {
        long startedAt = System.nanoTime();
        String messageId = messageId();
        AtomicInteger chunkIndex = new AtomicInteger();
        try {
            log.debug("AI SSE request started: messageId={}, uri={}", messageId, chatUri);
            webClient.post()
                    .uri(chatUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> addAuthorizationHeader(headers))
                    .bodyValue(request)
                    .exchangeToFlux(response -> toEventStream(response, messageId, startedAt))
                    .publishOn(Schedulers.boundedElastic(), 1)
                    .takeUntil(event -> "[DONE]".equals(event.data()))
                    .doOnNext(event -> consumeEvent(event, eventConsumer, messageId, startedAt, chunkIndex))
                    .blockLast(readTimeout);
        } catch (ChatAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ChatAiException("AI 챗봇 서버에 연결하지 못했습니다.", exception);
        }
    }

    private Flux<ServerSentEvent<String>> toEventStream(
            org.springframework.web.reactive.function.client.ClientResponse response,
            String messageId,
            long startedAt
    ) {
        log.debug(
                "AI SSE response headers received: messageId={}, status={}, elapsedMs={}",
                messageId,
                response.statusCode().value(),
                elapsedMillis(startedAt)
        );
        if (!response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMapMany(body -> Flux.error(new ChatAiException(
                            "AI 챗봇 호출에 실패했습니다: " + response.statusCode().value()
                    )));
        }
        return response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
        });
    }

    private void consumeEvent(
            ServerSentEvent<String> event,
            Consumer<ChatAiEvent> eventConsumer,
            String messageId,
            long startedAt,
            AtomicInteger chunkIndex
    ) {
        String data = event.data();
        if (data == null || data.isBlank()) {
            return;
        }
        if ("[DONE]".equals(data)) {
            log.debug("AI SSE done received: messageId={}, elapsedMs={}", messageId, elapsedMillis(startedAt));
            return;
        }
        ChatAiEvent chatAiEvent = parseEvent(data);
        log.debug(
                "AI SSE chunk received: messageId={}, chunkIndex={}, type={}, contentLength={}, elapsedMs={}",
                messageId,
                chunkIndex.incrementAndGet(),
                chatAiEvent.type(),
                chatAiEvent.content() == null ? 0 : chatAiEvent.content().length(),
                elapsedMillis(startedAt)
        );
        try (MDC.MDCCloseable ignored = MDC.putCloseable("chatMessageId", messageId)) {
            eventConsumer.accept(chatAiEvent);
        }
    }

    private void addAuthorizationHeader(HttpHeaders headers) {
        if (!internalAiToken.isBlank()) {
            headers.setBearerAuth(internalAiToken);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String messageId() {
        String messageId = MDC.get("chatMessageId");
        return messageId == null ? "unknown" : messageId;
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
