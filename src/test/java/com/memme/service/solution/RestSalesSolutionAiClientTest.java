package com.memme.service.solution;

import java.time.LocalDate;
import java.util.List;

import com.memme.dto.sales.SalesSolutionGenerationRequest;
import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.exception.SalesSolutionAiException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestSalesSolutionAiClientTest {

    private static final String BASE_URL = "http://ai.test";
    private static final String URL = BASE_URL + "/internal/v1/ai/solutions/generate";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private ValidatorFactory validatorFactory;
    private MockRestServiceServer server;
    private RestSalesSolutionAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        client = new RestSalesSolutionAiClient(
                builder,
                BASE_URL,
                "internal-token",
                validatorFactory.getValidator(),
                jsonMapper
        );
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void sendsActualAiContractAndKeepsEvidence() throws Exception {
        SalesSolutionGenerationRequest request = request();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(jsonMapper.writeValueAsString(request)))
                .andRespond(withSuccess(successBody("근거 문장"), MediaType.APPLICATION_JSON));

        SalesSolutionGenerationResponse response = client.generate(request);

        assertThat(response.data().targetDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(response.data().solutionCards().getFirst().evidence()).isEqualTo("근거 문장");
        server.verify();
    }

    @Test
    void acceptsNullableEvidenceFromAi() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(successBody(null), MediaType.APPLICATION_JSON));

        SalesSolutionGenerationResponse response = client.generate(request());

        assertThat(response.data().solutionCards().getFirst().evidence()).isNull();
        server.verify();
    }

    @Test
    void rejectsDuplicateRanks() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"message":"솔루션을 생성했습니다.","data":{
                          "targetDate":"2026-09-24","modelVersion":"claude-sonnet-4-5",
                          "solutionCards":[
                            {"rankNo":1,"title":"A","summaryText":"요약","detailText":"상세","evidence":null},
                            {"rankNo":1,"title":"B","summaryText":"요약","detailText":"상세","evidence":null}
                          ]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(SalesSolutionAiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_AI_RESPONSE"));
        server.verify();
    }

    @Test
    void mapsAiValidationError() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message":"요청 필드가 스키마와 일치하지 않습니다.",
                                 "status":"FAILED","error":{"code":"VALIDATION_ERROR","retryable":false},
                                 "data":null}
                                """));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(SalesSolutionAiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(422);
                    assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(exception.isRetryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void rejectsUploadWithoutSalesAnalysisIdBeforeHttpCall() {
        SalesSolutionGenerationRequest invalid = new SalesSolutionGenerationRequest(
                1L,
                null,
                LocalDate.of(2026, 9, 24),
                "UPLOAD",
                metrics()
        );

        assertThatThrownBy(() -> client.generate(invalid))
                .isInstanceOfSatisfying(SalesSolutionAiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_SOLUTION_REQUEST"));
    }

    private SalesSolutionGenerationRequest request() {
        return new SalesSolutionGenerationRequest(
                1L,
                56L,
                LocalDate.of(2026, 9, 24),
                "UPLOAD",
                metrics()
        );
    }

    private SalesSolutionMetrics metrics() {
        return new SalesSolutionMetrics(
                new SalesSolutionMetrics.SalesSummary(1_183_600L, -0.12),
                List.of(new SalesSolutionMetrics.HourlyPoint("WEEKDAY", 14, 30_000L)),
                List.of(new SalesSolutionMetrics.CategoryPoint("커피", 0.62, null)),
                1_250_000L,
                null
        );
    }

    private String successBody(String evidence) {
        String evidenceJson = evidence == null ? "null" : "\"" + evidence + "\"";
        return """
                {"message":"솔루션을 생성했습니다.","data":{
                  "targetDate":"2026-09-24","modelVersion":"claude-sonnet-4-5",
                  "solutionCards":[{"rankNo":1,"title":"프로모션 진행","summaryText":"요약",
                    "detailText":"상세", "evidence":%s}]}}
                """.formatted(evidenceJson);
    }
}
