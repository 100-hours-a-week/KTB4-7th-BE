package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import com.memme.service.store.StoreCoordinates;
import com.memme.exception.solution.ExternalDataLookupException;
import java.math.BigDecimal;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaVillageForecastServiceTest {

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void 광명_좌표로_격자와_조회가능한_최신_발표를_요청한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/1360000/VilageFcstInfoService_2.0/getVilageFcst");
                    assertThat(request.getURI().getRawQuery())
                            .contains("serviceKey=encoded%2Bkey%3D", "base_date=20261007", "base_time=1100",
                                    "nx=58", "ny=125", "dataType=JSON", "numOfRows=1000", "pageNo=1")
                            .doesNotContain("%252B");
                })
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                        "body":{"dataType":"JSON","numOfRows":1000,"pageNo":1,"totalCount":1,
                        "items":{"item":[{"baseDate":"20261007","baseTime":"1100","category":"POP",
                        "fcstDate":"20261007","fcstTime":"1200","fcstValue":"60","nx":58,"ny":125}]}}}}
                        """, APPLICATION_JSON));

        var result = service.fetch(new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646")));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().response().body().items().item()).hasSize(1);
        server.verify();
    }

    @Test
    void 발표_10분_전에는_이전_발표를_요청한다() {
        KmaVillageForecastService service = serviceAt("2026-10-06T17:05:00Z");
        server.expect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("base_date=20261006", "base_time=2300"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                        "body":{"totalCount":0,"items":{"item":[]}}}}
                        """, APPLICATION_JSON));

        assertThat(service.fetch(new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isEmpty();
        server.verify();
    }

    @Test
    void 오늘의_최저_최고_보충용으로_오늘_02시_발표를_조회한다() {
        KmaVillageForecastService service = serviceAt("2026-10-15T07:03:00Z");
        server.expect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("base_date=20261015", "base_time=0200", "nx=58", "ny=125"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                        "body":{"dataType":"JSON","numOfRows":1000,"pageNo":1,"totalCount":2,
                        "items":{"item":[
                        {"baseDate":"20261015","baseTime":"0200","category":"TMN",
                         "fcstDate":"20261015","fcstTime":"0600","fcstValue":"11","nx":58,"ny":125},
                        {"baseDate":"20261015","baseTime":"0200","category":"TMX",
                         "fcstDate":"20261015","fcstTime":"1500","fcstValue":"22","nx":58,"ny":125}]}}}}
                        """, APPLICATION_JSON));

        var result = service.fetchEarlyIssue(
                new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646")),
                LocalDate.of(2026, 10, 15));

        assertThat(result.orElseThrow().response().body().items().item())
                .extracting(item -> item.category()).containsExactly("TMN", "TMX");
        server.verify();
    }

    @Test
    void 오늘_02시_발표_전에는_미래_발표를_조회하지_않는다() {
        KmaVillageForecastService service = serviceAt("2026-10-14T17:05:00Z");

        assertThat(service.fetchEarlyIssue(
                new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646")),
                LocalDate.of(2026, 10, 15))).isEmpty();
        server.verify();
    }

    @Test
    void 기상청이_빈_items를_문자열로_보내도_정상_빈_결과로_처리한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {}).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                "body":{"dataType":"JSON","numOfRows":1000,"pageNo":1,"totalCount":0,"items":""}}}
                """, APPLICATION_JSON));

        assertThat(service.fetch(new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isEmpty();
        server.verify();
    }

    @Test
    void 성공_헤더에_본문이_없으면_빈_예보가_아닌_잘못된_응답으로_처리한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {}).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"}}}
                """, APPLICATION_JSON));

        assertThatThrownBy(() -> service.fetch(new StoreCoordinates(
                new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("KMA_INVALID_RESPONSE");
        server.verify();
    }

    @Test
    void 응답_헤더가_실패이면_빈_예보와_구분한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {}).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"},"body":{"totalCount":0}}}
                """, APPLICATION_JSON));

        assertThatThrownBy(() -> service.fetch(new StoreCoordinates(
                new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("KMA_PROVIDER_ERROR");
        server.verify();
    }

    @Test
    void 인증_오류에_인증키가_예외로_노출되지_않는다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {}).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> service.fetch(new StoreCoordinates(
                new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("KMA_AUTH_ERROR")
                .hasMessageNotContaining("encoded%2Bkey%3D");
        server.verify();
    }

    @Test
    void 전체_건수가_한_페이지를_넘으면_다음_페이지까지_조회한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("pageNo=1"))
                .andRespond(withSuccess(forecastPage(1001, "1200"), APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("pageNo=2"))
                .andRespond(withSuccess(forecastPage(1001, "1300"), APPLICATION_JSON));

        var result = service.fetch(new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646")));

        assertThat(result.orElseThrow().response().body().items().item())
                .extracting(item -> item.fcstTime())
                .containsExactly("1200", "1300");
        server.verify();
    }

    @Test
    void 연결_실패를_안전한_예외로_전달한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {}).andRespond(withException(new IOException("connection timeout")));

        assertThatThrownBy(() -> service.fetch(new StoreCoordinates(
                new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("KMA_CONNECTION_ERROR");
        server.verify();
    }

    @Test
    void 시간_초과를_연결_실패와_구분한다() {
        KmaVillageForecastService service = serviceAt("2026-10-07T02:11:00Z");
        server.expect(request -> {}).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.fetch(new StoreCoordinates(
                new BigDecimal("37.4786"), new BigDecimal("126.8646"))))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("KMA_TIMEOUT");
        server.verify();
    }

    private String forecastPage(int totalCount, String forecastTime) {
        return """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                "body":{"dataType":"JSON","numOfRows":1000,"pageNo":1,"totalCount":%d,
                "items":{"item":[{"baseDate":"20261007","baseTime":"1100","category":"POP",
                "fcstDate":"20261007","fcstTime":"%s","fcstValue":"60","nx":58,"ny":125}]}}}}
                """.formatted(totalCount, forecastTime);
    }

    private KmaVillageForecastService serviceAt(String instant) {
        return new KmaVillageForecastService(builder, "encoded%2Bkey%3D",
                Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul")));
    }
}
