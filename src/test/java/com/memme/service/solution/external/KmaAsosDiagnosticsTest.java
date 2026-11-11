package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.memme.exception.solution.ExternalDataLookupException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaAsosDiagnosticsTest {

    private static final String KEY = "secret%2Bkey%3D";
    private static final String PRIVATE_TEXT = "private-provider-response";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private final Logger logger = (Logger) LoggerFactory.getLogger(KmaAsosObservationClient.class);
    private ListAppender<ILoggingEvent> appender;
    private MockRestServiceServer server;
    private KmaAsosObservationClient client;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KmaAsosObservationClient(builder, KEY);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void HTTP_상태를_기록하고_오류_본문과_키는_노출하지_않는다(boolean noon) {
        server.expect(request -> {}).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body(KEY + PRIVATE_TEXT));
        fails(noon, "ASOS_LOOKUP_FAILED");
        assertLog(noon, "httpStatus=429", "timeout=false");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 중첩된_JDK_타임아웃을_분류한다(boolean noon) {
        server.expect(request -> {}).andRespond(request -> {
            throw new IOException("https://apis.data.go.kr?ServiceKey=" + KEY,
                    new HttpTimeoutException(PRIVATE_TEXT));
        });
        fails(noon, "ASOS_LOOKUP_FAILED");
        assertLog(noon, "httpStatus=UNAVAILABLE", "timeout=true");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 소켓_타임아웃도_분류한다(boolean noon) {
        server.expect(request -> {}).andRespond(request -> {
            throw new SocketTimeoutException(KEY);
        });
        fails(noon, "ASOS_LOOKUP_FAILED");
        assertLog(noon, "timeout=true");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 일반_IO_오류를_타임아웃으로_오인하지_않는다(boolean noon) {
        server.expect(request -> {}).andRespond(request -> { throw new IOException(KEY); });
        fails(noon, "ASOS_LOOKUP_FAILED");
        assertLog(noon, "timeout=false");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 공급자_결과_코드만_기록한다(boolean noon) {
        server.expect(request -> {}).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"30","resultMsg":"%s"}}}
                """.formatted(KEY + PRIVATE_TEXT), MediaType.APPLICATION_JSON));
        fails(noon, "ASOS_PROVIDER_ERROR");
        assertLog(noon, "providerCode=30", "httpStatus=200");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 공급자_코드에_임의_텍스트가_들어와도_기록하지_않는다(boolean noon) {
        server.expect(request -> {}).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"%s"}}}
                """.formatted(KEY + PRIVATE_TEXT), MediaType.APPLICATION_JSON));
        fails(noon, "ASOS_PROVIDER_ERROR");
        assertLog(noon, "providerCode=UNRECOGNIZED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 파싱_실패_본문을_기록하지_않는다(boolean noon) {
        server.expect(request -> {}).andRespond(withSuccess(KEY + PRIVATE_TEXT, MediaType.APPLICATION_JSON));
        fails(noon, "ASOS_INVALID_RESPONSE");
        assertLog(noon, "httpStatus=200");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 키_누락을_외부_호출_없이_구분한다(boolean noon) {
        client = new KmaAsosObservationClient(RestClient.builder(), "");
        fails(noon, "ASOS_KEY_MISSING");
        assertLog(noon, "httpStatus=UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 키_URI_형식_오류를_구분한다(boolean noon) {
        client = new KmaAsosObservationClient(RestClient.builder(), KEY + " invalid");
        fails(noon, "ASOS_KEY_INVALID");
        assertLog(noon, "httpStatus=UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 정상_빈_응답은_실패로_기록하지_않는다(boolean noon) {
        server.expect(request -> {}).andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount":0}}}
                """, MediaType.APPLICATION_JSON));
        if (noon) assertThat(client.dailyNoonWeather("108", DAY, DAY)).isEmpty();
        else assertThat(client.dailyRain("108", DAY, DAY)).isEmpty();
        assertThat(appender.list).isEmpty();
    }

    private void fails(boolean noon, String code) {
        assertThatThrownBy(() -> {
            if (noon) client.dailyNoonWeather("108", DAY, DAY);
            else client.dailyRain("108", DAY, DAY);
        }).isInstanceOf(ExternalDataLookupException.class).hasMessage(code);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("code=" + code);
    }

    private void assertLog(boolean noon, String... fields) {
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getFormattedMessage())
                .contains("type=" + (noon ? "NOON_WEATHER" : "DAILY_RAIN"),
                        "station=108", "from=2026-11-09", "to=2026-11-09", "page=1")
                .contains(fields)
                .doesNotContain(KEY, "secret+key=", PRIVATE_TEXT, "ServiceKey", "https://", "\n");
        assertThat(event.getThrowableProxy()).isNull();
    }
}
