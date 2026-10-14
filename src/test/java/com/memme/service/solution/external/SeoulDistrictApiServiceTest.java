package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.memme.exception.solution.ExternalDataLookupException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SeoulDistrictApiServiceTest {

    private static final String KEY = "testSeoulKey123";

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private SeoulDistrictApiService service;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SeoulDistrictApiService(builder, KEY, new SeoulDistrictResponseService());
    }

    @Test
    void 세_종류의_서울시_API를_실제_요청하고_응답을_파싱한다() {
        server.expect(request -> {
            assertThat(request.getURI().toString()).isEqualTo(
                    "http://openapi.seoul.go.kr:8088/" + KEY + "/json/VwsmAdstrdSelngW/1/1000/20253");
        }).andRespond(withSuccess(page("VwsmAdstrdSelngW", "THSMON_SELNG_AMT", 1200000), APPLICATION_JSON));
        server.expect(request -> {
            assertThat(request.getURI().toString()).isEqualTo(
                    "http://openapi.seoul.go.kr:8088/" + KEY + "/json/VwsmAdstrdStorW/1/1000/20253");
        }).andRespond(withSuccess(page("VwsmAdstrdStorW", "SIMILR_INDUTY_STOR_CO", 40), APPLICATION_JSON));
        server.expect(request -> {
            assertThat(request.getURI().toString()).isEqualTo(
                    "http://openapi.seoul.go.kr:8088/" + KEY + "/json/VwsmAdstrdFlpopW/1/1000");
        }).andRespond(withSuccess(page("VwsmAdstrdFlpopW", "TOT_FLPOP_CO", 10000), APPLICATION_JSON));

        assertThat(service.estimatedSales(1, 1000, "20253").orElseThrow().data().row().getFirst().salesAmount())
                .isEqualTo(1200000L);
        assertThat(service.storeCount(1, 1000, "20253").orElseThrow().data().row().getFirst().storeCount())
                .isEqualTo(40L);
        assertThat(service.footTraffic(1, 1000).orElseThrow().data().row().getFirst().totalCount())
                .isEqualTo(10000L);
        server.verify();
    }

    @Test
    void 데이터가_없으면_빈_결과를_반환한다() {
        server.expect(request -> {}).andRespond(withSuccess(
                "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"데이터 없음\"}}", APPLICATION_JSON));

        assertThat(service.estimatedSales(1, 1000, null)).isEmpty();
        server.verify();
    }

    @Test
    void 기준_분기_필터가_없으면_기본_조회_경로를_사용한다() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/" + KEY + "/json/VwsmAdstrdSelngW/3/7"))
                .andRespond(withSuccess("{\"RESULT\":{\"CODE\":\"INFO-200\"}}", APPLICATION_JSON));

        assertThat(service.estimatedSales(3, 7, null)).isEmpty();
        server.verify();
    }

    @Test
    void 인증키가_없으면_요청하지_않는다() {
        var missingKeyService = new SeoulDistrictApiService(builder, "", new SeoulDistrictResponseService());

        assertThatThrownBy(() -> missingKeyService.storeCount(1, 5, null))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_KEY_MISSING");
        server.verify();
    }

    @Test
    void 한_번에_천_건을_넘는_요청은_외부_호출_전에_차단한다() {
        assertThatThrownBy(() -> service.storeCount(1, 1001, "20253"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void 잘못된_분기나_인증키는_URL에_삽입하지_않는다() {
        assertThatThrownBy(() -> service.storeCount(1, 5, "20253/other"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeoulDistrictApiService(builder, "key/other", new SeoulDistrictResponseService())
                .estimatedSales(1, 5, null))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageNotContaining("key/other");
        server.verify();
    }

    @Test
    void HTTP_인증_실패_예외에_인증키를_노출하지_않는다() {
        server.expect(request -> {}).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> service.estimatedSales(1, 5, null))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_AUTH_ERROR")
                .hasMessageNotContaining(KEY);
        server.verify();
    }

    @Test
    void 시간_초과_예외에_요청_URL과_인증키를_노출하지_않는다() {
        server.expect(request -> {}).andRespond(withException(new SocketTimeoutException("timeout " + KEY)));

        assertThatThrownBy(() -> service.footTraffic(1, 5))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_TIMEOUT")
                .hasMessageNotContaining(KEY);
        server.verify();
    }

    @Test
    void 서버_오류를_안전한_코드로_변환한다() {
        server.expect(request -> {}).andRespond(withServerError());

        assertThatThrownBy(() -> service.storeCount(1, 5, null))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_HTTP_ERROR")
                .hasMessageNotContaining(KEY);
        server.verify();
    }

    @Test
    void 연결_실패를_안전한_코드로_변환한다() {
        server.expect(request -> {}).andRespond(withException(new IOException("connection " + KEY)));

        assertThatThrownBy(() -> service.estimatedSales(1, 5, null))
                .isInstanceOf(ExternalDataLookupException.class)
                .hasMessageContaining("SEOUL_CONNECTION_ERROR")
                .hasMessageNotContaining(KEY);
        server.verify();
    }

    private String page(String serviceName, String field, long value) {
        return """
                {"%s":{"list_total_count":1,"RESULT":{"CODE":"INFO-000","MESSAGE":"정상"},
                "row":[{"STDR_YYQU_CD":"20253","ADSTRD_CD":"1168051000",
                "SVC_INDUTY_CD":"CS100010","%s":%d}]}}
                """.formatted(serviceName, field, value);
    }
}
