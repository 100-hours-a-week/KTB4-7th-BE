package com.memme.service.sales.forecast;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import com.memme.dto.sales.SalesForecastBatchRequest;
import com.memme.dto.sales.SalesForecastBatchResponse;
import com.memme.dto.sales.SalesForecastStatus;
import com.memme.exception.SalesForecastAiException;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestSalesForecastAiClientTest {

    private static final String BASE_URL = "http://ai.test";
    private static final String FORECAST_URL = BASE_URL + "/internal/v1/ai/forecast/batch";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private ValidatorFactory validatorFactory;
    private MockRestServiceServer server;
    private RestSalesForecastAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        client = new RestSalesForecastAiClient(
                builder,
                BASE_URL,
                "test-internal-token",
                validatorFactory.getValidator(),
                jsonMapper
        );
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void callsForecastEndpointWithBearerTokenAndReturnsCompletedResponse() throws Exception {
        SalesForecastBatchRequest request = validRequest();
        SalesForecastBatchResponse response = completedResponse();

        server.expect(requestTo(FORECAST_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(jsonMapper.writeValueAsString(request)))
                .andRespond(withSuccess(jsonMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        SalesForecastBatchResponse result = client.createForecast(request);

        assertThat(result.data().horizonDays()).isEqualTo(35);
        assertThat(result.data().predictions()).hasSize(35);
        server.verify();
    }

    @Test
    void omitsAuthorizationHeaderWhenTokenIsBlankAndReturnsInsufficientHistory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer tokenlessServer = MockRestServiceServer.bindTo(builder).build();
        RestSalesForecastAiClient tokenlessClient = new RestSalesForecastAiClient(
                builder,
                BASE_URL,
                " ",
                validatorFactory.getValidator(),
                jsonMapper
        );
        tokenlessServer.expect(requestTo(FORECAST_URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("""
                        {
                          "message": "예측에 필요한 매출 이력이 부족합니다.",
                          "status": "INSUFFICIENT_DATA",
                          "data": {
                            "missingData": ["INSUFFICIENT_HISTORY"],
                            "incompleteMonths": ["2026-07"],
                            "requiredTrainingRows": 60,
                            "providedTrainingRows": 31
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        SalesForecastBatchResponse result = tokenlessClient.createForecast(validRequest());

        assertThat(result.status()).isEqualTo(SalesForecastStatus.INSUFFICIENT_DATA);
        assertThat(result.data().missingData()).containsExactly("INSUFFICIENT_HISTORY");
        tokenlessServer.verify();
    }

    @Test
    void convertsValidationErrorWithoutRetry() {
        server.expect(requestTo(FORECAST_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "message": "forecastStartDate가 올바르지 않습니다.",
                                  "status": "FAILED",
                                  "error": {"code": "VALIDATION_ERROR", "retryable": false},
                                  "data": null
                                }
                                """));

        assertThatThrownBy(() -> client.createForecast(validRequest()))
                .isInstanceOfSatisfying(SalesForecastAiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(422);
                    assertThat(exception.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(exception.isRetryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void convertsGatewayTimeoutAsRetryable() {
        server.expect(requestTo(FORECAST_URL))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "message": "예측 처리 시간이 초과되었습니다.",
                                  "status": "FAILED",
                                  "error": {"code": "PROVIDER_TIMEOUT", "retryable": true},
                                  "data": null
                                }
                                """));

        assertThatThrownBy(() -> client.createForecast(validRequest()))
                .isInstanceOfSatisfying(SalesForecastAiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(504);
                    assertThat(exception.getCode()).isEqualTo("PROVIDER_TIMEOUT");
                    assertThat(exception.isRetryable()).isTrue();
                });
        server.verify();
    }

    @Test
    void rejectsMalformedCompletedResponse() throws Exception {
        SalesForecastBatchResponse response = completedResponse();
        SalesForecastBatchResponse malformed = new SalesForecastBatchResponse(
                response.message(),
                response.status(),
                new SalesForecastBatchResponse.Data(
                        response.data().forecastStartDate(),
                        response.data().forecastEndDate(),
                        34,
                        response.data().predictions(),
                        null
                )
        );
        server.expect(requestTo(FORECAST_URL))
                .andRespond(withSuccess(jsonMapper.writeValueAsString(malformed), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createForecast(validRequest()))
                .isInstanceOfSatisfying(SalesForecastAiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_AI_RESPONSE");
                    assertThat(exception.isRetryable()).isFalse();
                });
        server.verify();
    }

    @Test
    void rejectsInvalidRequestBeforeCallingAi() {
        SalesForecastBatchRequest invalidRequest = new SalesForecastBatchRequest(
                1L, 2L, null, null, List.of());

        assertThatThrownBy(() -> client.createForecast(invalidRequest))
                .isInstanceOfSatisfying(SalesForecastAiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_FORECAST_REQUEST");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void convertsConnectionFailureAsRetryable() {
        RestClient.Builder failingBuilder = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new IOException("connection refused");
                });
        RestSalesForecastAiClient failingClient = new RestSalesForecastAiClient(
                failingBuilder,
                BASE_URL,
                "",
                validatorFactory.getValidator(),
                jsonMapper
        );

        assertThatThrownBy(() -> failingClient.createForecast(validRequest()))
                .isInstanceOfSatisfying(SalesForecastAiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("AI_CONNECTION_ERROR");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    private SalesForecastBatchRequest validRequest() {
        return new SalesForecastBatchRequest(
                1L,
                2L,
                3L,
                LocalDate.of(2026, 9, 1),
                List.of(new SalesForecastBatchRequest.DailySales(
                        LocalDate.of(2026, 8, 31), 1_000_000L, 100L))
        );
    }

    private SalesForecastBatchResponse completedResponse() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        List<SalesForecastBatchResponse.Prediction> predictions = startDate.datesUntil(startDate.plusDays(35))
                .map(date -> new SalesForecastBatchResponse.Prediction(
                        date, 1_000_000L, 900_000L, 1_100_000L, "ridge-2026-09-16"))
                .toList();
        return new SalesForecastBatchResponse(
                "예측을 생성했습니다.",
                null,
                new SalesForecastBatchResponse.Data(
                        startDate,
                        startDate.plusDays(34),
                        35,
                        predictions,
                        null
                )
        );
    }
}
