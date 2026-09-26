package com.memme.service.chat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.memme.dto.chat.ChatAiRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestChatAiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void AI_SSE_청크와_완료_이벤트를_읽는다() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/ai/chat/messages", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(requestBody).contains("오늘 뭘 해야 해?");
            byte[] body = ("data: {\"event\":\"answerChunk\",\"data\":{"
                    + "\"content\":\"재고를 확인하세요.\","
                    + "\"evidence\":{\"metric\":\"sales_summary\"}}}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        RestChatAiClient client = new RestChatAiClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "",
                3,
                10,
                JsonMapper.builder().build()
        );
        List<ChatAiEvent> events = new ArrayList<>();

        client.stream(new ChatAiRequest(
                1L,
                2L,
                java.time.LocalDate.of(2026, 9, 26),
                "오늘 뭘 해야 해?",
                List.of(),
                List.of()
        ), events::add);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ChatAiEvent.Type.CHUNK);
            assertThat(event.content()).isEqualTo("재고를 확인하세요.");
            assertThat(event.evidenceJson()).contains("sales_summary");
        });
    }
}
