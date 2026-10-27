package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.memme.exception.solution.ExternalDataLookupException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaWeatherAlertServiceTest {

    private static final String SEOUL_SOUTHEAST_ADDRESS = "서울특별시 송파구 백제고분로45길 17-3";

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void 발효시각_기준으로_매장_권역에_해당하는_육상_특보를_조회하고_서울_상위권역_중복은_제거한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/api/typ01/url/wrn_now_data.php");
                    assertThat(request.getURI().getRawQuery())
                            .contains("fe=e", "tm=", "disp=0", "authKey=encoded%2Bkey%3D")
                            .doesNotContain("%252B");
                })
                .andRespond(withSuccess(response(
                        row("L1100000", "서울", "L1100000", "서울", "호우", "주의", "발표", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "호우", "주의", "발표", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "강풍", "경보", "발표", "202610211200"),
                        row("S1130000", "동해남부전해상", "S1130000", "동해남부전해상", "풍랑", "주의", "발표", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "대설", "예비", "발표", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "한파", "주의", "해제", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "강풍", "주의", "해제예고", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "호우", "주의", "미발효", "202610211200"),
                        row("L1100000", "서울", "L1100100", "서울동남권", "폭염", "주의", "발표", "202610211301")
                ), TEXT_PLAIN));

        var alerts = service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS);

        assertThat(alerts)
                .extracting(KmaWeatherAlert::name)
                .containsExactly("호우주의보", "강풍경보");
        assertThat(alerts)
                .extracting(KmaWeatherAlert::areaCode)
                .containsExactly("L1100000", "L1100100");
        assertThat(alerts.getFirst().effectiveAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 12, 0));
        server.verify();
    }

    @Test
    void 정상적인_빈_응답은_특보_없음으로_반환한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withSuccess("#START7777\n#7777END\n", TEXT_PLAIN));

        assertThat(service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS)).isEmpty();
        server.verify();
    }

    @Test
    void 응답_문자셋에_따라_특보명을_정상_디코딩한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        Charset eucKr = Charset.forName("EUC-KR");
        server.expect(request -> {}).andRespond(withSuccess(
                response(row("L1100000", "서울", "L1100100", "서울동남권",
                        "호우", "주의", "발표", "202610211200")).getBytes(eucKr),
                new org.springframework.http.MediaType("text", "plain", eucKr)));

        assertThat(service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .extracting(KmaWeatherAlert::name)
                .containsExactly("호우주의보");
        server.verify();
    }

    @Test
    void 문자셋이_없는_MS949_응답도_정상_디코딩한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        Charset ms949 = Charset.forName("MS949");
        server.expect(request -> {}).andRespond(withSuccess(
                response(row("L1100000", "서울", "L1100100", "서울동남권",
                        "호우", "주의", "발표", "202610211200")).getBytes(ms949), TEXT_PLAIN));

        assertThat(service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .extracting(KmaWeatherAlert::name)
                .containsExactly("호우주의보");
        server.verify();
    }

    @Test
    void 키가_없으면_요청하지_않고_인증_오류를_반환한다() {
        KmaWeatherAlertService service = service(" ");

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_KEY_MISSING");
        server.verify();
    }

    @Test
    void HTTP_인증_오류에_인증키를_예외로_노출하지_않는다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_AUTH_ERROR")
                .hasMessageNotContaining("encoded%2Bkey%3D");
        server.verify();
    }

    @Test
    void 응답본문의_인증키_오류도_특보없음이_아닌_인증오류로_분류한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withSuccess("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", TEXT_PLAIN));

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_AUTH_ERROR");
        server.verify();
    }

    @Test
    void 응답형식이_잘못되면_특보없음으로_오인하지_않는다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withSuccess("#START7777\nL1100100,서울동남권,주의\n#7777END", TEXT_PLAIN));

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_INVALID_RESPONSE");
        server.verify();
    }

    @Test
    void 발효시각이_유효하지_않으면_응답_오류로_분류한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withSuccess(response(
                row("L1100000", "서울", "L1100100", "서울동남권",
                        "호우", "주의", "발표", "202602300000")), TEXT_PLAIN));

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_INVALID_RESPONSE");
        server.verify();
    }

    @Test
    void 연결_실패는_특보없음이_아닌_연결_오류로_분류한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_CONNECTION_ERROR");
        server.verify();
    }

    @Test
    void 시간초과는_연결_실패와_다른_오류로_분류한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_TIMEOUT");
        server.verify();
    }

    @Test
    void API허브_공급자_오류를_정상_빈_결과와_구분한다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");
        server.expect(request -> {}).andRespond(withSuccess("PROVIDER ERROR: temporary failure", TEXT_PLAIN));

        assertThatThrownBy(() -> service.findActiveLandAlerts(SEOUL_SOUTHEAST_ADDRESS))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_PROVIDER_ERROR");
        server.verify();
    }

    @Test
    void 주소의_권역을_판별할_수_없으면_특보없음으로_위장하지_않는다() {
        KmaWeatherAlertService service = service("encoded%2Bkey%3D");

        assertThatThrownBy(() -> service.findActiveLandAlerts("알 수 없는 지역"))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessage("KMA_ALERT_AREA_UNMAPPED");
        server.verify();
    }

    @Test
    void 송파구를_서울동남권과_적용가능한_상위_권역으로_매핑한다() {
        var areaCodes = new KmaWeatherAlertAreaMapper().applicableAreaCodes(SEOUL_SOUTHEAST_ADDRESS);

        assertThat(areaCodes).contains("L1100100", "L1100000", "L1000000");
    }

    private KmaWeatherAlertService service(String serviceKey) {
        return new KmaWeatherAlertService(builder, serviceKey,
                Clock.fixed(Instant.parse("2026-10-21T04:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private String response(String... rows) {
        return "#START7777\n"
                + "#REG_UP,REG_UP_KO,REG_ID,REG_KO,TM_FC,TM_EF,WRN,LVL,CMD\n"
                + String.join("\n", rows)
                + "\n#7777END\n";
    }

    private String row(String parentCode, String parentName, String areaCode, String areaName,
                       String warning, String level, String command, String effectiveAt) {
        return String.join(",", parentCode, parentName, areaCode, areaName,
                "202610211100", effectiveAt, warning, level, command);
    }
}
