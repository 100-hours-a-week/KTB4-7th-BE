package com.memme.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 운영 ALB는 실제 클라이언트 IP를 X-Forwarded-For 맨 오른쪽에 붙인다. native 전략(Tomcat RemoteIpValve)에서는
 * 클라이언트가 왼쪽에 넣은 위조 값을 무시하고 오른쪽 IP로 가입 제한을 적용하는지 실제 HTTP로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.forward-headers-strategy=native",
        "app.signup-rate-limit.max-requests=2"
})
class SignupRateLimitForwardedIpIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void native_전략에서는_X_Forwarded_For_왼쪽_위조값을_바꿔도_ALB가_붙인_IP로_제한한다() throws Exception {
        String token = csrfToken();

        assertThat(signup(token, 1, "198.51.100.1, 203.0.113.9").statusCode()).isEqualTo(201);
        assertThat(signup(token, 2, "198.51.100.2, 203.0.113.9").statusCode()).isEqualTo(201);
        HttpResponse<String> limited = signup(token, 3, "198.51.100.3, 203.0.113.9");

        assertThat(limited.statusCode()).as(limited.body()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(signup(token, 4, "198.51.100.4, 203.0.113.10").statusCode()).isEqualTo(201);
    }

    private String csrfToken() throws Exception {
        HttpResponse<Void> response = client.send(HttpRequest.newBuilder(uri("/v1/auth/csrf-token")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> signup(String token, int sequence, String forwardedFor) throws Exception {
        String body = """
                {"email":"limit%d@memme.com","password":"Password1!","passwordConfirm":"Password1!",
                 "phone":"0109000%04d","agreements":{"termsOfService":true,"termsOfServiceVersion":"2026-09",
                 "privacyPolicy":true,"privacyPolicyVersion":"2026-09"}}
                """.formatted(sequence, sequence);
        HttpRequest request = HttpRequest.newBuilder(uri("/v1/auth/signup/account"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
