package com.memme.dto.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RankingGrowthDtoTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 조회_기간을_생략하면_지난달을_선택한다() {
        assertThat(new RankingGrowthRequest(null).period()).isEqualTo(RankingPeriod.LAST_MONTH);
        assertThat(new RankingGrowthRequest(RankingPeriod.THIS_MONTH).period())
                .isEqualTo(RankingPeriod.THIS_MONTH);
    }

    @Test
    void 지난달_완료_응답은_익명_상위_목록과_별도_내_순위를_직렬화한다() {
        var response = new RankingGrowthResponse(
                "조회에 성공했습니다.",
                RankingGrowthResponse.Status.COMPLETED,
                new RankingGrowthResponse.Data(
                        true,
                        new RankingGrowthResponse.Period(
                                RankingPeriod.LAST_MONTH,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30),
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 31),
                                OffsetDateTime.parse("2026-10-06T00:10:00+09:00")),
                        List.of(new RankingGrowthResponse.Ranking(1, "사장님 184", new BigDecimal("28.5"), false)),
                        new RankingGrowthResponse.MyRanking(21, new BigDecimal("12.3"), false),
                        new RankingGrowthResponse.MyEligibility(
                                RankingGrowthResponse.EligibilityStatus.ELIGIBLE, null)));

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("message").asString()).isEqualTo("조회에 성공했습니다.");
        assertThat(json.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(json.path("data").path("isFinal").booleanValue()).isTrue();
        assertThat(json.path("data").path("period").path("type").asString()).isEqualTo("LAST_MONTH");
        assertThat(json.path("data").path("period").path("startDate").asString()).isEqualTo("2026-09-01");
        assertThat(json.path("data").path("period").path("comparisonEndDate").asString())
                .isEqualTo("2026-08-31");
        assertThat(json.path("data").path("period").path("calculatedAt").asString())
                .isEqualTo("2026-10-06T00:10:00+09:00");
        assertThat(json.path("data").path("rankings").get(0).path("displayName").asString())
                .isEqualTo("사장님 184");
        assertThat(json.path("data").path("rankings").get(0).path("growthRate").decimalValue())
                .isEqualByComparingTo("28.5");
        assertThat(json.path("data").path("myRanking").path("rank").intValue()).isEqualTo(21);
        assertThat(json.path("data").path("myRanking").path("includedInTop20").booleanValue()).isFalse();
        assertThat(json.path("data").path("myEligibility").path("status").asString())
                .isEqualTo("ELIGIBLE");
        assertThat(json.path("data").path("rankings").get(0).has("salesAmount")).isFalse();
        assertThat(json.path("data").path("rankings").get(0).has("storeId")).isFalse();
    }

    @Test
    void 데이터_부족과_집계_전_상태를_표현한다() {
        var insufficient = new RankingGrowthResponse(
                "아직 순위 정보가 없어요. 매출 데이터를 등록하면 랭킹에 참여할 수 있어요.",
                RankingGrowthResponse.Status.COMPLETED,
                new RankingGrowthResponse.Data(
                        false,
                        new RankingGrowthResponse.Period(
                                RankingPeriod.THIS_MONTH,
                                LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 5),
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 5),
                                OffsetDateTime.parse("2026-10-06T00:10:00+09:00")),
                        List.of(),
                        null,
                        new RankingGrowthResponse.MyEligibility(
                                RankingGrowthResponse.EligibilityStatus.DATA_INSUFFICIENT,
                                RankingGrowthResponse.EligibilityReason.DATA_INCOMPLETE)));
        var pending = new RankingGrowthResponse(
                "아직 성장 랭킹이 집계되지 않았어요.",
                RankingGrowthResponse.Status.NOT_CALCULATED,
                new RankingGrowthResponse.Data(
                        true,
                        new RankingGrowthResponse.Period(
                                RankingPeriod.LAST_MONTH,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30),
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 31),
                                null),
                        List.of(),
                        null,
                        new RankingGrowthResponse.MyEligibility(
                                RankingGrowthResponse.EligibilityStatus.UNKNOWN, null)));

        var insufficientJson = mapper.readTree(mapper.writeValueAsString(insufficient));
        var pendingJson = mapper.readTree(mapper.writeValueAsString(pending));

        assertThat(insufficientJson.path("data").path("rankings").isEmpty()).isTrue();
        assertThat(insufficientJson.path("data").path("myRanking").isNull()).isTrue();
        assertThat(insufficientJson.path("data").path("myEligibility").path("reason").asString())
                .isEqualTo("DATA_INCOMPLETE");
        assertThat(pendingJson.path("status").asString()).isEqualTo("NOT_CALCULATED");
        assertThat(pendingJson.path("data").path("period").path("type").asString())
                .isEqualTo("LAST_MONTH");
        assertThat(pendingJson.path("data").path("period").path("startDate").asString())
                .isEqualTo("2026-09-01");
        assertThat(pendingJson.path("data").path("period").path("comparisonEndDate").asString())
                .isEqualTo("2026-08-31");
        assertThat(pendingJson.path("data").path("period").has("calculatedAt")).isTrue();
        assertThat(pendingJson.path("data").path("period").path("calculatedAt").isNull()).isTrue();
        assertThat(pendingJson.path("data").path("myEligibility").path("reason").isNull()).isTrue();
    }
}
