package com.memme.service.store.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.service.store.storage.MenuImageFile;
import com.memme.service.store.storage.S3MenuImageStorage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.json.JsonMapper;

class MenuExternalAdapterIntegrationTest {

    private static final String BUCKET = "menu-integration-test";
    private static final byte[] IMAGE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};

    @Test
    void S3_저장_서명_URL_AI_HTTP_분석_응답이_연결된다() throws Exception {
        Map<String, byte[]> objects = new ConcurrentHashMap<>();
        AtomicReference<Throwable> aiFailure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/" + BUCKET + "/", exchange -> handleObject(exchange, objects));
        server.createContext("/internal/v2/ai/menu-recognition", exchange -> {
            try {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                        .isEqualTo("Bearer test-internal-token");
                var request = JsonMapper.builder().build().readTree(exchange.getRequestBody());
                assertThat(request.path("storeId").asLong()).isEqualTo(7);
                assertThat(request.path("batchId").asLong()).isEqualTo(15);
                assertThat(request.path("images").size()).isEqualTo(1);
                var image = request.path("images").get(0);
                URI signedUrl = URI.create(image.path("imageUrl").asString());
                assertThat(signedUrl.getQuery()).contains("X-Amz-Signature=", "X-Amz-Expires=600");
                byte[] downloaded = HttpClient.newHttpClient()
                        .send(HttpRequest.newBuilder(signedUrl).GET().build(),
                                HttpResponse.BodyHandlers.ofByteArray()).body();
                assertThat(downloaded).isEqualTo(IMAGE);
                respond(exchange, 200, ("""
                        {"message":"메뉴가 인식되었습니다.","data":{"detectedItems":[
                        {"sourceImageId":%d,"name":"아메리카노","price":4000,
                        "category":"COFFEE","confidence":0.98,"priceReviewRequired":false}]}}
                        """).formatted(image.path("imageId").asLong()));
            } catch (Throwable failure) {
                aiFailure.set(failure);
                respond(exchange, 500, "{}");
            }
        });
        server.start();

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret"));
        var s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        S3Client s3Client = S3Client.builder().endpointOverride(endpoint).region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials).serviceConfiguration(s3Configuration).build();
        S3Presigner presigner = S3Presigner.builder().endpointOverride(endpoint).region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials).serviceConfiguration(s3Configuration).build();
        try (S3MenuImageStorage storage = new S3MenuImageStorage(s3Client, presigner, BUCKET)) {
            String key = storage.store(new MenuImageFile(7, "image/jpeg", IMAGE));
            assertThat(objects).containsKey("/" + BUCKET + "/" + key);
            URI imageUrl = storage.temporaryReadUrl(key);
            var request = new MenuRecognitionRequest(7, 15,
                    List.of(new MenuRecognitionRequest.Image(52, imageUrl.toString(), 1)));
            var client = new RestMenuRecognitionAiClient(endpoint.toString(), "test-internal-token", 3, 10);

            MenuRecognitionResponse response;
            try {
                response = client.recognize(request);
            } catch (MenuRecognitionAiException exception) {
                if (aiFailure.get() != null) {
                    throw new AssertionError("로컬 AI 대역에서 요청 검증 실패", aiFailure.get());
                }
                throw exception;
            }

            assertThat(aiFailure.get()).isNull();
            assertThat(response.data().detectedItems()).hasSize(1);
            assertThat(response.data().detectedItems().getFirst().sourceImageId()).isEqualTo(52);
            assertThat(response.data().detectedItems().getFirst().price()).isEqualTo(4000);
            assertThat(response.data().detectedItems().getFirst().confidence())
                    .isEqualByComparingTo(new BigDecimal("0.98"));
            storage.delete(key);
            assertThat(objects).doesNotContainKey("/" + BUCKET + "/" + key);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private void handleObject(HttpExchange exchange, Map<String, byte[]> objects) throws IOException {
        String path = exchange.getRequestURI().getPath();
        switch (exchange.getRequestMethod()) {
            case "PUT" -> {
                byte[] received = exchange.getRequestBody().readAllBytes();
                String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
                if (encoding != null && encoding.contains("aws-chunked")) {
                    received = decodeAwsChunks(received);
                }
                objects.put(path, received);
                respond(exchange, 200, "");
            }
            case "GET" -> {
                byte[] image = objects.get(path);
                if (image == null) {
                    respond(exchange, 404, "");
                } else {
                    exchange.sendResponseHeaders(200, image.length);
                    try (var body = exchange.getResponseBody()) {
                        body.write(image);
                    }
                }
            }
            case "DELETE" -> {
                objects.remove(path);
                respond(exchange, 204, "");
            }
            default -> respond(exchange, 405, "");
        }
    }

    private byte[] decodeAwsChunks(byte[] encoded) {
        String text = new String(encoded, StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int offset = 0;
        while (true) {
            int headerEnd = text.indexOf("\r\n", offset);
            int size = Integer.parseInt(text.substring(offset, headerEnd).split(";", 2)[0], 16);
            offset = headerEnd + 2;
            if (size == 0) {
                return decoded.toByteArray();
            }
            decoded.writeBytes(Arrays.copyOfRange(encoded, offset, offset + size));
            offset += size + 2;
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
