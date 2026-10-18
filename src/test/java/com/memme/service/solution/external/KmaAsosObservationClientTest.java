package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaAsosObservationClientTest {

    private MockRestServiceServer server;
    private KmaAsosObservationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KmaAsosObservationClient(builder, "encoded%2Bkey%3D");
    }

    @Test
    void 별도_키_없이_공식_ASOS_지점_좌표를_읽는다() {
        assertThat(client.stations())
                .hasSize(95)
                .contains(new KmaAsosObservationClient.Station("108",
                        new BigDecimal("37.5714"), new BigDecimal("126.9658")));
        server.verify();
    }

    @Test
    void 일강수_0은_무강수로_보존하고_결측값은_보존하지_않는다() {
        server.expect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("ServiceKey=encoded%2Bkey%3D", "dataCd=ASOS", "dateCd=DAY",
                                "stnIds=108", "startDt=20261016", "endDt=20261017", "dataType=JSON"))
                .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"00\"},"
                        + "\"body\":{\"totalCount\":2,\"items\":{\"item\":["
                        + "{\"tm\":\"2026-10-16\",\"stnId\":\"108\",\"sumRn\":\"0.0\"},"
                        + "{\"tm\":\"2026-10-17\",\"stnId\":\"108\",\"sumRn\":\"\"}"
                        + "]}}}}", APPLICATION_JSON));

        assertThat(client.dailyRain("108", LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 17)))
                .containsEntry(LocalDate.of(2026, 10, 16), new BigDecimal("0.0"))
                .doesNotContainKey(LocalDate.of(2026, 10, 17));
        server.verify();
    }

    @Test
    void 오류_본문을_빈_자료로_오인하지_않는다() {
        server.expect(request -> {}).andRespond(withSuccess("AUTH ERROR", APPLICATION_JSON));

        assertThatThrownBy(() -> client.dailyRain("108", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 17)))
                .isInstanceOf(RuntimeException.class);
        server.verify();
    }

    @Test
    void 공급자_오류도_빈_관측값으로_오인하지_않는다() {
        server.expect(request -> {}).andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"30\"}}}", APPLICATION_JSON));

        assertThatThrownBy(() -> client.dailyRain("108", LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 17))).isInstanceOf(RuntimeException.class);
        server.verify();
    }
}
