package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.dto.sales.SalesSolutionMetrics.WeatherContext;
import com.memme.dto.solution.external.KmaVillageForecastResponse;
import com.memme.dto.solution.external.KmaVillageForecastResponse.Item;
import com.memme.dto.solution.external.KmaVillageForecastResponse.Items;
import com.memme.dto.solution.external.KmaVillageForecastResponse.Response;
import com.memme.dto.solution.external.KmaVillageForecastResponse.Body;
import com.memme.entity.store.StoreBusinessHours;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeatherContextSummaryServiceTest {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String TODAY = "20261007";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T01:30:00Z"), KOREA);

    private WeatherContextSummaryService service;

    @BeforeEach
    void setUp() {
        service = new WeatherContextSummaryService(CLOCK);
    }

    @Test
    void 오늘_남은_영업시간만_요약하고_POP를_소수로_변환한다() {
        List<Item> items = forecastHours(9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        add(items, 9, "SKY", "4");
        add(items, 9, "POP", "100");
        add(items, 9, "TMP", "40");
        add(items, 9, "PTY", "1");
        add(items, 9, "PCP", "20.0");
        add(items, 18, "POP", "90");
        add(items, 18, "TMP", "40");
        add(items, 18, "PTY", "1");
        add(items, 18, "PCP", "20.0");
        add(items, 11, "SKY", "3");
        add(items, 11, "POP", "60");
        add(items, 11, "TMP", "24");
        add(items, 11, "PTY", "0");
        add(items, 11, "PCP", "1.0mm 미만");
        add(items, 12, "SKY", "3");
        add(items, 12, "POP", "70");
        add(items, 12, "TMP", "25");
        add(items, 12, "PTY", "1");
        add(items, 12, "PCP", "강수없음");
        for (int hour = 13; hour < 18; hour++) {
            add(items, hour, "SKY", "3");
        }
        add(items, 13, "TMN", "14");
        add(items, 15, "TMX", "31");

        WeatherContext result = service.summarize(forecast(items), businessHours(9, 18)).orElseThrow();

        assertThat(result.basisDateTime()).isEqualTo("2026-10-07 08:00");
        assertThat(result.skyText()).isEqualTo("구름많음");
        assertThat(result.minTempC()).isEqualTo(12);
        assertThat(result.maxTempC()).isEqualTo(31);
        assertThat(result.maxPop()).isEqualTo(0.7);
        assertThat(result.rainHours()).containsExactly(11, 12);
        assertThat(result.precipitationMm()).isNull();
        assertThat(result.isNotable()).isTrue();
    }

    @Test
    void PCP가_모두_정확한_값이면_남은_영업시간의_강수량을_합산한다() {
        List<Item> items = forecastHours(10, 11, 12, 13, 14, 15, 16, 17);
        for (int hour = 10; hour < 18; hour++) {
            add(items, hour, "SKY", "1");
            add(items, hour, "POP", "20");
            add(items, hour, "TMP", "20");
            add(items, hour, "PTY", "0");
            add(items, hour, "PCP", hour == 12 ? "1.5" : hour == 14 ? "2.0mm" : "강수없음");
        }
        add(items, 7, "TMN", "10");
        add(items, 7, "TMX", "20");

        WeatherContext result = service.summarize(forecast(items), businessHours(9, 18)).orElseThrow();

        assertThat(result.precipitationMm()).isEqualTo(3.5);
        assertThat(result.maxPop()).isEqualTo(0.2);
        assertThat(result.rainHours()).isEmpty();
        assertThat(result.isNotable()).isFalse();
    }

    @Test
    void 강수확률이_낮아도_남은_영업시간의_극단_기온이면_주목_날씨로_판정한다() {
        List<Item> items = forecastHours(10, 11, 12, 13, 14, 15, 16, 17);
        addCompleteRequiredValues(items);
        add(items, 14, "TMP", "33");

        WeatherContext hotResult = service.summarize(forecast(items), businessHours(9, 18)).orElseThrow();

        assertThat(hotResult.maxPop()).isEqualTo(0.2);
        assertThat(hotResult.rainHours()).isEmpty();
        assertThat(hotResult.isNotable()).isTrue();

        add(items, 14, "TMP", "20");
        add(items, 15, "TMP", "-12");

        WeatherContext coldResult = service.summarize(forecast(items), businessHours(9, 18)).orElseThrow();

        assertThat(coldResult.isNotable()).isTrue();
    }

    @Test
    void 휴무_영업종료_또는_영업시간이_없으면_요약하지_않는다() {
        List<Item> items = forecastHours(10, 11, 12, 13, 14, 15, 16, 17);
        addCompleteRequiredValues(items);

        assertThat(service.summarize(forecast(items), businessHoursClosed())).isEmpty();
        assertThat(service.summarize(forecast(items), businessHours(9, 10))).isEmpty();
        assertThat(service.summarize(forecast(items), List.of())).isEmpty();
    }

    @Test
    void 요일별_영업시작_전이면_영업시작부터_영업시간을_집계한다() {
        List<Item> items = forecastHours(9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        addCompleteRequiredValues(items);
        add(items, 12, "POP", "80");

        WeatherContext result = service.summarize(forecast(items), businessHours(12, 18)).orElseThrow();

        assertThat(result.maxPop()).isEqualTo(0.8);
        assertThat(result.rainHours()).containsExactly(12);
    }

    @Test
    void 자정을_넘는_영업시간도_날씨는_매출과_같은_달력_날짜로만_요약한다() {
        WeatherContextSummaryService lateNightService = new WeatherContextSummaryService(
                Clock.fixed(Instant.parse("2026-10-07T14:00:00Z"), KOREA)
        );
        List<Item> items = new ArrayList<>();
        addOnDate(items, "20261007", 23, "SKY", "1");
        addOnDate(items, "20261007", 23, "POP", "20");
        addOnDate(items, "20261007", 23, "TMP", "20");
        addOnDate(items, "20261007", 23, "PTY", "0");
        addOnDate(items, "20261007", 23, "PCP", "강수없음");
        addOnDate(items, "20261008", 0, "SKY", "4");
        addOnDate(items, "20261008", 0, "POP", "100");
        addOnDate(items, "20261008", 0, "TMP", "40");
        addOnDate(items, "20261008", 0, "PTY", "1");
        addOnDate(items, "20261008", 0, "PCP", "10.0");
        addOnDate(items, "20261008", 1, "SKY", "4");
        addOnDate(items, "20261008", 1, "POP", "100");
        addOnDate(items, "20261008", 1, "TMP", "40");
        addOnDate(items, "20261008", 1, "PTY", "1");
        addOnDate(items, "20261008", 1, "PCP", "10.0");
        add(items, 6, "TMN", "12");
        add(items, 15, "TMX", "24");

        WeatherContext result = lateNightService.summarize(forecast(items), businessHours(22, 2)).orElseThrow();

        assertThat(result.maxPop()).isEqualTo(0.2);
        assertThat(result.rainHours()).isEmpty();
        assertThat(result.precipitationMm()).isEqualTo(0.0);
        assertThat(result.isNotable()).isFalse();
    }

    @Test
    void 오늘_자정_이후에는_전날의_익일마감_영업시간을_오늘_영업시간으로_포함한다() {
        WeatherContextSummaryService afterMidnightService = new WeatherContextSummaryService(
                Clock.fixed(Instant.parse("2026-10-07T15:30:00Z"), KOREA)
        );
        List<Item> items = new ArrayList<>();
        for (int hour = 0; hour <= 1; hour++) {
            addOnDate(items, "20261008", hour, "SKY", "4");
            addOnDate(items, "20261008", hour, "POP", "80");
            addOnDate(items, "20261008", hour, "TMP", "20");
            addOnDate(items, "20261008", hour, "PTY", "1");
            addOnDate(items, "20261008", hour, "PCP", "2.0");
        }
        addOnDate(items, "20261008", 6, "TMN", "12");
        addOnDate(items, "20261008", 15, "TMX", "24");

        List<StoreBusinessHours> hours = List.of(
                businessHours(3, 22, 2),
                closedBusinessHours(4)
        );
        WeatherContext result = afterMidnightService.summarize(forecast(items), hours).orElseThrow();

        assertThat(result.maxPop()).isEqualTo(0.8);
        assertThat(result.rainHours()).containsExactly(0, 1);
        assertThat(result.precipitationMm()).isEqualTo(4.0);
        assertThat(result.isNotable()).isTrue();
    }

    @Test
    void 영업시간_중_필수_예보값이_누락되면_부분_날씨정보를_만들지_않는다() {
        List<Item> items = forecastHours(10, 11, 12, 13, 14, 15, 16, 17);
        addCompleteRequiredValues(items);
        items.removeIf(item -> item.fcstTime().equals("1300") && item.category().equals("POP"));

        assertThat(service.summarize(forecast(items), businessHours(9, 18))).isEmpty();
    }

    private List<Item> forecastHours(int... hours) {
        List<Item> items = new ArrayList<>();
        for (int hour : hours) {
            add(items, hour, "SKY", "1");
            add(items, hour, "POP", "20");
            add(items, hour, "TMP", "20");
            add(items, hour, "PTY", "0");
            add(items, hour, "PCP", "강수없음");
        }
        add(items, 6, "TMN", "12");
        add(items, 15, "TMX", "24");
        return items;
    }

    private void addCompleteRequiredValues(List<Item> items) {
        for (int hour = 10; hour < 18; hour++) {
            add(items, hour, "SKY", "1");
            add(items, hour, "POP", "20");
            add(items, hour, "TMP", "20");
            add(items, hour, "PTY", "0");
            add(items, hour, "PCP", "강수없음");
        }
        add(items, 6, "TMN", "12");
        add(items, 15, "TMX", "24");
    }

    private void add(List<Item> items, int hour, String category, String value) {
        String forecastTime = "%02d00".formatted(hour);
        items.add(new Item("20261007", "0800", category, TODAY, forecastTime, value, 58, 125));
    }

    private void addOnDate(List<Item> items, String forecastDate, int hour, String category, String value) {
        String forecastTime = "%02d00".formatted(hour);
        items.add(new Item("20261007", "0800", category, forecastDate, forecastTime, value, 58, 125));
    }

    private KmaVillageForecastResponse forecast(List<Item> items) {
        return new KmaVillageForecastResponse(new Response(
                new KmaVillageForecastResponse.Header("00", "NORMAL_SERVICE"),
                new Body("JSON", items.size(), 1, items.size(), new Items(items))
        ));
    }

    private List<StoreBusinessHours> businessHours(int openHour, int closeHour) {
        return List.of(businessHours(3, openHour, closeHour));
    }

    private List<StoreBusinessHours> businessHoursClosed() {
        return List.of(closedBusinessHours(3));
    }

    private StoreBusinessHours businessHours(int dayOfWeek, int openHour, int closeHour) {
        return StoreBusinessHours.create(
                null, dayOfWeek, LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0), false,
                LocalDateTime.of(2026, 10, 7, 0, 0));
    }

    private StoreBusinessHours closedBusinessHours(int dayOfWeek) {
        return StoreBusinessHours.create(
                null, dayOfWeek, null, null, true, LocalDateTime.of(2026, 10, 7, 0, 0));
    }
}
