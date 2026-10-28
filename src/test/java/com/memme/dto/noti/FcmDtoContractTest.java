package com.memme.dto.noti;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.memme.entity.noti.NotificationType;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FcmDtoContractTest {

    @Test
    void 토큰은_필수이고_4096자를_넘을_수_없다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertTrue(validator.validate(new FcmTokenRequest("valid-token")).isEmpty());
            assertTrue(validator.validate(new FcmTokenRequest("x".repeat(4096))).isEmpty());
            assertFalse(validator.validate(new FcmTokenRequest(null)).isEmpty());
            assertFalse(validator.validate(new FcmTokenRequest("  ")).isEmpty());
            assertFalse(validator.validate(new FcmTokenRequest("x".repeat(4097))).isEmpty());
        }
    }

    @Test
    void 저장된_알림을_세_유형_공통_FCM_메시지로_변환한다() {
        for (NotificationType type : NotificationType.values()) {
            var item = new NotificationListResponse.Item(
                    17L, type, "제목", "내용", "SOLUTION_BUNDLE", 42L,
                    OffsetDateTime.parse("2026-10-28T08:00:00+09:00"), null);

            var message = FcmPushMessage.from("device-token", item);

            assertEquals("device-token", message.token());
            assertEquals("제목", message.notification().title());
            assertEquals("내용", message.notification().body());
            assertEquals("17", message.data().get("notificationId"));
            assertEquals(type.name(), message.data().get("type"));
            assertEquals("SOLUTION_BUNDLE", message.data().get("relatedEntityType"));
            assertEquals("42", message.data().get("relatedEntityId"));
        }
    }

    @Test
    void 연결_대상이_없으면_FCM_data에서_관련_필드를_생략한다() {
        var item = new NotificationListResponse.Item(
                18L, NotificationType.SALES_UPLOAD_REMINDER, "제목", "내용", null, null,
                OffsetDateTime.parse("2026-10-28T08:00:00+09:00"), null);

        var message = FcmPushMessage.from("device-token", item);

        assertEquals(2, message.data().size());
        assertFalse(message.data().containsKey("relatedEntityType"));
        assertFalse(message.data().containsKey("relatedEntityId"));
    }

    @Test
    void DTO의_기본_문자열_표현에_토큰을_노출하지_않는다() {
        var item = new NotificationListResponse.Item(
                18L, NotificationType.RANKING_CHANGED, "제목", "내용", null, null,
                OffsetDateTime.parse("2026-10-28T08:00:00+09:00"), null);

        assertFalse(new FcmTokenRequest("secret-token").toString().contains("secret-token"));
        assertFalse(FcmPushMessage.from("secret-token", item).toString().contains("secret-token"));
    }
}
