package com.memme.dto.solution;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionResponseContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesTodayCompletedShape() throws Exception {
        var response = new SolutionTodayResponse(
                "조회에 성공했습니다.",
                "COMPLETED",
                new SolutionTodayResponse.Data(
                        "맴매카페",
                        "맴매카페 맴매 솔루션",
                        81L,
                        LocalDate.of(2026, 9, 8),
                        List.of(card())
                )
        );

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("data").path("solutionBundleId").longValue()).isEqualTo(81L);
        assertThat(json.path("data").path("solutionCards").get(0).path("evidence").asString())
                .isEqualTo("근거");
        assertThat(json.path("data").has("cards")).isFalse();
    }

    @Test
    void omitsCompletedHeaderFieldsWhileGenerating() throws Exception {
        var response = new SolutionTodayResponse(
                "오늘의 솔루션을 생성하고 있습니다.",
                "GENERATING",
                new SolutionTodayResponse.Data(
                        null,
                        null,
                        81L,
                        LocalDate.of(2026, 9, 8),
                        List.of()
                )
        );

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("data").has("storeName")).isFalse();
        assertThat(json.path("data").has("screenTitle")).isFalse();
        assertThat(json.path("data").path("solutionCards").isArray()).isTrue();
    }

    @Test
    void serializesBundleSaveAndSavedSolutionShapes() throws Exception {
        var detail = new SolutionBundleDetailResponse(
                "조회에 성공했습니다.",
                new SolutionBundleDetailResponse.Data(
                        new SolutionBundleDetailResponse.SolutionBundle(
                                81L,
                                LocalDate.of(2026, 9, 8),
                                OffsetDateTime.of(
                                        2026, 9, 9, 0, 0, 0, 0,
                                        ZoneOffset.ofHours(9)
                                ),
                                "오늘의 솔루션은 00:00시에 사라져요. 남겨두려면 저장해주세요.",
                                false,
                                List.of(card())
                        )
                )
        );
        var save = new SolutionSaveResponse(
                "솔루션이 저장되었습니다.",
                new SolutionSaveResponse.Data(
                        new SolutionSaveResponse.SavedSolution(
                                91L,
                                81L,
                                LocalDate.of(2026, 9, 8),
                                LocalDateTime.of(2026, 9, 8, 12, 0)
                        ),
                        "SOL-03"
                )
        );
        var savedDetail = new SavedSolutionDetailResponse(
                "조회에 성공했습니다.",
                new SavedSolutionDetailResponse.Data(
                        new SavedSolutionDetailResponse.SavedSolution(
                                91L,
                                LocalDate.of(2026, 9, 8),
                                "09/08 점심 시간대 할인 외 2개",
                                true,
                                List.of(new SavedSolutionDetailResponse.Item(
                                        1, "점심 시간대 할인", "요약", "상세", "근거"
                                ))
                        )
                )
        );

        var detailJson = mapper.readTree(mapper.writeValueAsString(detail));
        var saveJson = mapper.readTree(mapper.writeValueAsString(save));
        var savedDetailJson = mapper.readTree(mapper.writeValueAsString(savedDetail));

        assertThat(detailJson.path("data").path("solutionBundle").path("items").size())
                .isEqualTo(1);
        assertThat(saveJson.path("data").path("savedSolution").path("solutionBundleId")
                .longValue()).isEqualTo(81L);
        assertThat(saveJson.path("data").path("next").asString()).isEqualTo("SOL-03");
        assertThat(savedDetailJson.path("data").path("savedSolution").path("readOnly")
                .booleanValue()).isTrue();
        assertThat(savedDetailJson.path("data").path("savedSolution").path("items").get(0)
                .has("id")).isFalse();
    }

    @Test
    void serializesSavedListDeleteAndNavigationErrors() throws Exception {
        var list = new SavedSolutionListResponse(
                "조회에 성공했습니다.",
                91L,
                new SavedSolutionListResponse.Data(List.of(
                        new SavedSolutionListResponse.YearGroup(
                                2026,
                                List.of(new SavedSolutionListResponse.Item(
                                        91L,
                                        LocalDate.of(2026, 9, 8),
                                        "09/08 점심 시간대 할인 외 2개",
                                        "점심 시간대 할인",
                                        2
                                ))
                        )
                ))
        );
        var delete = new SavedSolutionDeleteResponse(
                "삭제되었습니다.",
                new SavedSolutionDeleteResponse.Data(3)
        );
        var expired = new SolutionNavigationErrorResponse(
                "오늘의 솔루션이 만료되었습니다.",
                "SOL-01-01",
                null
        );

        var listJson = mapper.readTree(mapper.writeValueAsString(list));
        var deleteJson = mapper.readTree(mapper.writeValueAsString(delete));
        var expiredJson = mapper.readTree(mapper.writeValueAsString(expired));

        assertThat(listJson.path("nextCursor").longValue()).isEqualTo(91L);
        assertThat(listJson.path("data").has("nextCursor")).isFalse();
        assertThat(deleteJson.path("data").path("deletedCount").intValue()).isEqualTo(3);
        assertThat(expiredJson.path("next").asString()).isEqualTo("SOL-01-01");
        assertThat(expiredJson.path("data").isNull()).isTrue();
    }

    private SolutionCardResponse card() {
        return new SolutionCardResponse(1L, 1, "점심 시간대 할인", "요약", "상세", "근거");
    }
}
