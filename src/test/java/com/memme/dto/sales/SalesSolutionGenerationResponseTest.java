package com.memme.dto.sales;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSolutionGenerationResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesEvidenceForEachSolutionCard() throws Exception {
        var response = new SalesSolutionGenerationResponse(
                null,
                List.of(new SalesSolutionGenerationResponse.SolutionCard(
                        1,
                        "점심 시간대 할인",
                        "점심 할인 프로모션을 제안합니다.",
                        "12시부터 14시까지 할인 행사를 진행하세요.",
                        "최근 4주간 평일 점심 주문 수가 다른 시간대보다 18% 낮았습니다.")),
                "anthropic:claude-sonnet-4-5");

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("solutionCards").get(0).path("evidence").asString())
                .isEqualTo("최근 4주간 평일 점심 주문 수가 다른 시간대보다 18% 낮았습니다.");
        assertThat(json.has("promptVersion")).isFalse();
    }

    @Test
    void deserializesEvidenceFromAiResponse() throws Exception {
        var response = mapper.readValue("""
                {
                  "targetDate": "2026-09-23",
                  "solutionCards": [
                    {
                      "rankNo": 1,
                      "title": "점심시간 집중 프로모션으로 평일 매출 회복",
                      "summaryText": "점심 매출 회복을 위한 프로모션을 제안합니다.",
                      "detailText": "점심시간에 집중 프로모션을 진행하세요.",
                      "evidence": "평일 12시 매출이 210,000원으로 시간대별 최고 매출을 기록했습니다."
                    }
                  ],
                  "modelVersion": "anthropic:claude-sonnet-4-5"
                }
                """, SalesSolutionGenerationResponse.class);

        assertThat(response.solutionCards().get(0).evidence())
                .isEqualTo("평일 12시 매출이 210,000원으로 시간대별 최고 매출을 기록했습니다.");
    }
}
