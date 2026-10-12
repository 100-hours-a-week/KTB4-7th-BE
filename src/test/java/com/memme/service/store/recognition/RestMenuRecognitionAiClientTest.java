package com.memme.service.store.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestMenuRecognitionAiClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void 배치와_서명_URL을_전달하고_후보를_읽는다() {
        server.expect(requestTo("http://ai.test/internal/v2/ai/menu-recognition"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer internal-token"))
                .andExpect(jsonPath("$.storeId").value(7))
                .andExpect(jsonPath("$.batchId").value(15))
                .andExpect(jsonPath("$.images[0].imageId").value(52))
                .andExpect(jsonPath("$.images[0].imageUrl")
                        .value("https://private.test/image?signature=test"))
                .andRespond(withSuccess("""
                        {"message":"메뉴가 인식되었습니다.","data":{"detectedItems":[
                          {"sourceImageId":52,"name":"아메리카노","price":4000,
                           "category":"COFFEE","confidence":0.98,"priceReviewRequired":false}]}}
                        """, MediaType.APPLICATION_JSON));

        var response = new RestMenuRecognitionAiClient(builder, "http://ai.test", "internal-token")
                .recognize(request());

        assertThat(response.data().detectedItems()).hasSize(1);
        assertThat(response.data().detectedItems().getFirst().price()).isEqualTo(4000);
        server.verify();
    }

    @Test
    void 식별_실패_422는_구분된_실패_사유로_반환한다() {
        server.expect(requestTo("http://ai.test/internal/v2/ai/menu-recognition"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"code\":\"LOW_IMAGE_QUALITY\"}}"));

        assertThatThrownBy(() -> new RestMenuRecognitionAiClient(builder, "http://ai.test", "")
                .recognize(request()))
                .isInstanceOf(MenuRecognitionAiException.class)
                .extracting("failReason")
                .isEqualTo("LOW_IMAGE_QUALITY");
        server.verify();
    }

    @Test
    void 다른_배치의_이미지_ID가_오면_응답을_거부한다() {
        server.expect(requestTo("http://ai.test/internal/v2/ai/menu-recognition"))
                .andRespond(withSuccess("""
                        {"data":{"detectedItems":[{"sourceImageId":999,"name":"라떼"}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new RestMenuRecognitionAiClient(builder, "http://ai.test", "")
                .recognize(request()))
                .isInstanceOf(MenuRecognitionAiException.class)
                .extracting("failReason")
                .isEqualTo("AI_PROCESSING_ERROR");
        server.verify();
    }

    @Test
    void AI_서버_오류는_분석_실패로_격리한다() {
        server.expect(requestTo("http://ai.test/internal/v2/ai/menu-recognition"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> new RestMenuRecognitionAiClient(builder, "http://ai.test", "")
                .recognize(request()))
                .isInstanceOf(MenuRecognitionAiException.class)
                .extracting("failReason")
                .isEqualTo("AI_PROCESSING_ERROR");
        server.verify();
    }

    private MenuRecognitionRequest request() {
        return new MenuRecognitionRequest(7, 15, List.of(new MenuRecognitionRequest.Image(
                52, "https://private.test/image?signature=test", 1)));
    }
}
