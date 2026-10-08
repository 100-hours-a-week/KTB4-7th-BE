package com.memme.dto.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.StatusResponse;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class MenuDtoContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    @Test
    void 메뉴_목록은_저장_메뉴와_임시_배치를_분리한다() {
        var response = new ApiResponse<>("조회에 성공했습니다.", new MenuListResponse(
                4,
                List.of(new MenuListResponse.Item(27, 1, "아메리카노", 4000, "COFFEE",
                        OffsetDateTime.parse("2026-10-07T13:00:00+09:00"))),
                new MenuListResponse.DraftBatch(15, "COMPLETED",
                        OffsetDateTime.parse("2026-10-07T13:15:00+09:00"), null, null)
        ));

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("data").path("menuRevision").asLong()).isEqualTo(4);
        assertThat(json.path("data").path("items").get(0).path("price").asLong()).isEqualTo(4000);
        assertThat(json.path("data").path("draftBatch").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(json.path("data").path("draftBatch").path("savedAt").isNull()).isTrue();
    }

    @Test
    void 수정과_확정_요청의_선택적_가격_확인_필드를_읽는다() {
        var patch = mapper.readValue("""
                {"expectedMenuRevision":4,"items":[{"menuId":27,"name":"아메리카노","price":4500,"category":"COFFEE","order":1}]}
                """, MenuPatchRequest.class);
        var confirmation = mapper.readValue("""
                {"expectedMenuRevision":4,"items":[{"itemId":101,"name":"아메리카노","price":4000,"category":"COFFEE","order":1,"priceConfirmed":true}]}
                """, MenuConfirmationRequest.class);

        assertThat(patch.items().getFirst().priceConfirmed()).isNull();
        assertThat(confirmation.items().getFirst().priceConfirmed()).isTrue();
        assertThat(confirmation.items().getFirst().itemId()).isEqualTo(101);
    }

    @Test
    void 업로드와_배치_상태는_최상위_status를_가진다() {
        var upload = new StatusResponse<>("메뉴판 이미지를 접수하고 AI 분석을 시작했습니다.", "PENDING",
                new MenuImageUploadResponse(15, 3, OffsetDateTime.parse("2026-10-07T13:15:00+09:00"), "MENU_LIST"));
        var batch = new StatusResponse<>("메뉴 인식이 완료되었습니다.", "COMPLETED",
                new MenuBatchResponse(15, 4, OffsetDateTime.parse("2026-10-07T13:15:00+09:00"),
                        null, null, List.of(new MenuBatchResponse.DetectedItem(
                        101, 1, "아메리카노", 4000L, "COFFEE", false))));

        assertThat(mapper.readTree(mapper.writeValueAsString(upload)).path("status").asString())
                .isEqualTo("PENDING");
        assertThat(mapper.readTree(mapper.writeValueAsString(batch)).path("data")
                .path("detectedItems").get(0).path("priceReviewRequired").asBoolean()).isFalse();
    }

    @Test
    void 행_검증은_서비스가_422로_처리하도록_원본_입력을_보존한다() {
        var request = new MenuConfirmationRequest(4L, List.of(
                new MenuConfirmationRequest.Item(101L, " ", -1L, "UNKNOWN", 1, false)));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request)).isEmpty();
            assertThat(validator.validate(new MenuConfirmationRequest(null, null)))
                    .hasSize(2);
        }
    }

    @Test
    void 가격은_JSON_정수만_허용하고_문자열과_소수는_행별_검증_대상으로_넘긴다() {
        var textPrice = mapper.readValue("""
                {"expectedMenuRevision":4,"items":[{"menuId":27,"price":"4000"}]}
                """, MenuPatchRequest.class);
        var decimalPrice = mapper.readValue("""
                {"expectedMenuRevision":4,"items":[{"itemId":101,"price":4000.5}]}
                """, MenuConfirmationRequest.class);
        var integerPrice = mapper.readValue("""
                {"expectedMenuRevision":4,"items":[{"itemId":101,"price":4000}]}
                """, MenuConfirmationRequest.class);

        assertThat(textPrice.items().getFirst().price()).isNull();
        assertThat(decimalPrice.items().getFirst().price()).isNull();
        assertThat(integerPrice.items().getFirst().price()).isEqualTo(4000L);
    }
}
